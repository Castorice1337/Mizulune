#include "UpdateClient.h"

#include "ProfileStore.h"

#include <QDir>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QMetaObject>
#include <QPointer>
#include <QRegularExpression>
#include <QUrl>

#include <algorithm>
#include <functional>
#include <thread>
#include <vector>
#include <windows.h>
#include <winhttp.h>

namespace loader {

namespace {
QString loaderRevisionFromAsset(const QString& name) {
    static const QRegularExpression re(
        QStringLiteral("^(?:MizuluneLoaderSDK|OpenZenLoader)-([A-Za-z0-9._-]+)\\.(?:zip|exe)$"));
    const QRegularExpressionMatch match = re.match(name);
    return match.hasMatch() ? match.captured(1) : QString();
}

std::wstring toWide(const QString& value) {
    return reinterpret_cast<const wchar_t*>(value.utf16());
}

QString winHttpError(const QString& prefix) {
    return QStringLiteral("%1 WinHTTP error %2").arg(prefix).arg(GetLastError());
}

bool queryContentLength(HINTERNET request, qint64& outLength) {
    DWORD size = sizeof(DWORD);
    DWORD value = 0;
    if (WinHttpQueryHeaders(request,
                            WINHTTP_QUERY_CONTENT_LENGTH | WINHTTP_QUERY_FLAG_NUMBER,
                            WINHTTP_HEADER_NAME_BY_INDEX,
                            &value,
                            &size,
                            WINHTTP_NO_HEADER_INDEX)) {
        outLength = value;
        return true;
    }
    outLength = -1;
    return false;
}

bool httpGet(const QUrl& url,
             const std::function<bool(const QByteArray&, qint64, qint64)>& onChunk,
             std::atomic_bool* cancel,
             QString& error) {
    if (!url.isValid() || url.scheme() != QStringLiteral("https")) {
        error = QStringLiteral("Only valid https URLs are supported.");
        return false;
    }

    URL_COMPONENTS parts;
    ZeroMemory(&parts, sizeof(parts));
    parts.dwStructSize = sizeof(parts);

    std::wstring urlText = toWide(url.toString(QUrl::FullyEncoded));
    parts.dwSchemeLength = DWORD(-1);
    parts.dwHostNameLength = DWORD(-1);
    parts.dwUrlPathLength = DWORD(-1);
    parts.dwExtraInfoLength = DWORD(-1);
    if (!WinHttpCrackUrl(urlText.c_str(), 0, 0, &parts)) {
        error = winHttpError(QStringLiteral("Failed to parse URL."));
        return false;
    }

    const QString host = QString::fromWCharArray(parts.lpszHostName, parts.dwHostNameLength);
    QString path = QString::fromWCharArray(parts.lpszUrlPath, parts.dwUrlPathLength);
    if (parts.dwExtraInfoLength > 0) {
        path += QString::fromWCharArray(parts.lpszExtraInfo, parts.dwExtraInfoLength);
    }
    if (path.isEmpty()) path = QStringLiteral("/");

    HINTERNET session = WinHttpOpen(L"Mizulune-Loader/1.0",
                                    WINHTTP_ACCESS_TYPE_DEFAULT_PROXY,
                                    WINHTTP_NO_PROXY_NAME,
                                    WINHTTP_NO_PROXY_BYPASS,
                                    0);
    if (!session) {
        error = winHttpError(QStringLiteral("Failed to open HTTP session."));
        return false;
    }

    DWORD redirects = WINHTTP_OPTION_REDIRECT_POLICY_ALWAYS;
    WinHttpSetOption(session, WINHTTP_OPTION_REDIRECT_POLICY, &redirects, sizeof(redirects));

    HINTERNET connect = WinHttpConnect(session,
                                       toWide(host).c_str(),
                                       parts.nPort,
                                       0);
    if (!connect) {
        error = winHttpError(QStringLiteral("Failed to connect."));
        WinHttpCloseHandle(session);
        return false;
    }

    HINTERNET request = WinHttpOpenRequest(connect,
                                           L"GET",
                                           toWide(path).c_str(),
                                           nullptr,
                                           WINHTTP_NO_REFERER,
                                           WINHTTP_DEFAULT_ACCEPT_TYPES,
                                           WINHTTP_FLAG_SECURE);
    if (!request) {
        error = winHttpError(QStringLiteral("Failed to open request."));
        WinHttpCloseHandle(connect);
        WinHttpCloseHandle(session);
        return false;
    }

    const wchar_t* headers = L"Accept: application/vnd.github+json\r\n";
    bool ok = WinHttpSendRequest(request,
                                 headers,
                                 DWORD(-1),
                                 WINHTTP_NO_REQUEST_DATA,
                                 0,
                                 0,
                                 0)
            && WinHttpReceiveResponse(request, nullptr);
    if (!ok) {
        error = winHttpError(QStringLiteral("Request failed."));
        WinHttpCloseHandle(request);
        WinHttpCloseHandle(connect);
        WinHttpCloseHandle(session);
        return false;
    }

    DWORD status = 0;
    DWORD statusSize = sizeof(status);
    if (WinHttpQueryHeaders(request,
                            WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
                            WINHTTP_HEADER_NAME_BY_INDEX,
                            &status,
                            &statusSize,
                            WINHTTP_NO_HEADER_INDEX)
            && (status < 200 || status >= 300)) {
        error = QStringLiteral("HTTP %1").arg(status);
        WinHttpCloseHandle(request);
        WinHttpCloseHandle(connect);
        WinHttpCloseHandle(session);
        return false;
    }

    qint64 total = -1;
    queryContentLength(request, total);
    qint64 received = 0;

    while (!cancel || !cancel->load()) {
        DWORD available = 0;
        if (!WinHttpQueryDataAvailable(request, &available)) {
            error = winHttpError(QStringLiteral("Failed while reading response."));
            ok = false;
            break;
        }
        if (available == 0) break;

        std::vector<char> buffer(std::min<DWORD>(available, 64 * 1024));
        DWORD read = 0;
        if (!WinHttpReadData(request, buffer.data(), static_cast<DWORD>(buffer.size()), &read)) {
            error = winHttpError(QStringLiteral("Failed while reading response."));
            ok = false;
            break;
        }
        if (read == 0) break;

        QByteArray chunk(buffer.data(), static_cast<int>(read));
        received += read;
        if (!onChunk(chunk, received, total)) {
            error = QStringLiteral("Download was cancelled.");
            ok = false;
            break;
        }
    }

    if (cancel && cancel->load()) {
        error = QStringLiteral("Download was cancelled.");
        ok = false;
    }

    WinHttpCloseHandle(request);
    WinHttpCloseHandle(connect);
    WinHttpCloseHandle(session);
    return ok;
}

ReleaseInfo parseReleaseInfo(const QByteArray& payload, QString& error) {
    QJsonParseError parseError;
    const QJsonDocument doc = QJsonDocument::fromJson(payload, &parseError);
    if (parseError.error != QJsonParseError::NoError || !doc.isObject()) {
        error = QStringLiteral("Invalid GitHub release response.");
        return {};
    }

    const QJsonObject root = doc.object();
    ReleaseInfo info;
    info.tagName = root.value(QStringLiteral("tag_name")).toString();
    info.title = root.value(QStringLiteral("name")).toString();
    info.body = root.value(QStringLiteral("body")).toString();
    info.htmlUrl = root.value(QStringLiteral("html_url")).toString();

    const QJsonArray assets = root.value(QStringLiteral("assets")).toArray();
    for (const QJsonValue& value : assets) {
        const QJsonObject asset = value.toObject();
        const QString name = asset.value(QStringLiteral("name")).toString();
        const QString revision = loaderRevisionFromAsset(name);
        if (revision.isEmpty()) continue;
        info.assetName = name;
        info.assetRevision = revision.left(7);
        info.assetDownloadUrl = asset.value(QStringLiteral("browser_download_url")).toString();
        info.hasLoaderAsset = !info.assetDownloadUrl.isEmpty();
        break;
    }

    const QString current = UpdateClient::currentRevision();
    const bool sameRevision = !current.isEmpty()
            && info.assetRevision.compare(current, Qt::CaseInsensitive) == 0;
    info.updateAvailable = info.hasLoaderAsset
            && !info.assetRevision.isEmpty()
            && (current.isEmpty() || !sameRevision);
    return info;
}
} // namespace

UpdateClient::UpdateClient(QObject* parent)
        : QObject(parent),
          cancelDownload_(std::make_shared<std::atomic_bool>(false)) {
}

UpdateClient::~UpdateClient() {
    cancelDownload();
}

void UpdateClient::setRepository(const QString& owner, const QString& repo) {
    if (!owner.trimmed().isEmpty()) owner_ = owner.trimmed();
    if (!repo.trimmed().isEmpty()) repo_ = repo.trimmed();
}

ReleaseInfo UpdateClient::latestRelease() const {
    return latest_;
}

QString UpdateClient::downloadPath() const {
    return downloadPath_;
}

QString UpdateClient::currentRevision() {
#ifdef OPENZEN_BUILD_REVISION
    return QString::fromLatin1(OPENZEN_BUILD_REVISION).left(7);
#else
    return QString();
#endif
}

void UpdateClient::checkLatestRelease() {
    bool expected = false;
    if (!checkInFlight_.compare_exchange_strong(expected, true)) return;

    const QString owner = owner_;
    const QString repo = repo_;
    QPointer<UpdateClient> self(this);
    emit checkStarted();

    std::thread([self, owner, repo]() {
        QByteArray payload;
        QString error;
        const QUrl url(QStringLiteral("https://api.github.com/repos/%1/%2/releases/latest")
                       .arg(owner, repo));
        const bool ok = httpGet(url, [&payload](const QByteArray& chunk, qint64, qint64) {
            payload.append(chunk);
            return true;
        }, nullptr, error);

        ReleaseInfo info;
        if (ok) info = parseReleaseInfo(payload, error);

        if (!self) return;
        QMetaObject::invokeMethod(self, [self, ok, info, error]() {
            if (!self) return;
            self->setCheckInFlight(false);
            if (!ok || !error.isEmpty()) {
                emit self->checkFailed(error);
                return;
            }
            self->latest_ = info;
            emit self->latestReleaseChanged();
        }, Qt::QueuedConnection);
    }).detach();
}

void UpdateClient::downloadLatestLoader() {
    bool expected = false;
    if (!downloadInFlight_.compare_exchange_strong(expected, true)) return;

    if (!latest_.hasLoaderAsset || latest_.assetDownloadUrl.isEmpty()) {
        setDownloadInFlight(false);
        emit downloadFailed(QStringLiteral("Latest release does not contain a loader package asset."));
        return;
    }

    QString error;
    if (!ProfileStore::ensureDirectories(&error)) {
        setDownloadInFlight(false);
        emit downloadFailed(error);
        return;
    }

    cancelDownload_->store(false);
    downloadPath_ = QDir(ProfileStore::updatesDirectory()).filePath(latest_.assetName);
    tempDownloadPath_ = downloadPath_ + QStringLiteral(".part");
    QFile::remove(tempDownloadPath_);

    const QString url = latest_.assetDownloadUrl;
    const QString finalPath = downloadPath_;
    const QString tempPath = tempDownloadPath_;
    auto cancelFlag = cancelDownload_;
    QPointer<UpdateClient> self(this);
    emit downloadStarted(finalPath);

    std::thread([self, url, finalPath, tempPath, cancelFlag]() {
        QFile file(tempPath);
        QString error;
        if (!file.open(QIODevice::WriteOnly | QIODevice::Truncate)) {
            error = file.errorString();
        } else {
            const bool ok = httpGet(QUrl(url), [&file, self, cancelFlag](const QByteArray& chunk, qint64 received, qint64 total) {
                if (!self || cancelFlag->load()) return false;
                if (file.write(chunk) != chunk.size()) return false;
                QMetaObject::invokeMethod(self, [self, received, total]() {
                    if (self) emit self->downloadProgress(received, total);
                }, Qt::QueuedConnection);
                return true;
            }, cancelFlag.get(), error);
            file.flush();
            file.close();
            if (ok) {
                QFile::remove(finalPath);
                if (!QFile::rename(tempPath, finalPath)) {
                    error = QStringLiteral("Failed to move downloaded loader into place.");
                }
            }
        }

        if (!self) return;
        QMetaObject::invokeMethod(self, [self, finalPath, tempPath, error]() {
            if (!self) return;
            self->setDownloadInFlight(false);
            if (!error.isEmpty()) {
                QFile::remove(tempPath);
                emit self->downloadFailed(error);
                return;
            }
            emit self->downloadFinished(finalPath);
        }, Qt::QueuedConnection);
    }).detach();
}

void UpdateClient::cancelDownload() {
    cancelDownload_->store(true);
}

void UpdateClient::setCheckInFlight(bool inFlight) {
    checkInFlight_.store(inFlight);
}

void UpdateClient::setDownloadInFlight(bool inFlight) {
    downloadInFlight_.store(inFlight);
}

} // namespace loader
