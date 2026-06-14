#include "MainWindow.h"

#include "ProfileStore.h"
#include "UpdateClient.h"
#include "loader.h"

#include <QApplication>
#include <QCloseEvent>
#include <QCoreApplication>
#include <QDir>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QMessageBox>
#include <QMetaObject>
#include <QMouseEvent>
#include <QPropertyAnimation>
#include <QResizeEvent>
#include <QUrl>
#include <QWindow>
#include <QStandardPaths>

#include <algorithm>
#include <cmath>
#include <string>
#include <thread>

#include <dwmapi.h>
#include <windows.h>

#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#  define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif
#ifndef DWMWA_SYSTEMBACKDROP_TYPE
#  define DWMWA_SYSTEMBACKDROP_TYPE 38
#endif
#ifndef DWMWCP_ROUND
#  define DWMWCP_ROUND 2
#endif
#ifndef DWMSBT_TRANSIENTWINDOW
#  define DWMSBT_TRANSIENTWINDOW 3
#endif

using Microsoft::WRL::Callback;

namespace loader {

namespace {
QString fromW(const std::wstring& value) {
    return QString::fromWCharArray(value.c_str(), static_cast<int>(value.size()));
}

bool startsWithMinecraft(const std::wstring& title) {
    static const std::wstring prefix = L"Minecraft";
    if (title.size() < prefix.size()) return false;
    return title.compare(0, prefix.size(), prefix) == 0;
}

bool isMinecraft(const std::wstring& title, const std::wstring& cls) {
    if (startsWithMinecraft(title)) return true;
    return _wcsicmp(cls.c_str(), L"GLFW30") == 0;
}

std::wstring toWide(const QString& value) {
    return value.toStdWString();
}

QString jsString(const QString& value) {
    return QString::fromUtf8(QJsonDocument(QJsonArray{value}).toJson(QJsonDocument::Compact))
            .mid(1)
            .chopped(1);
}

QString sourceWebUiDir() {
#ifdef OPENZEN_LOADER_WEBUI_DIR
    return QString::fromUtf8(OPENZEN_LOADER_WEBUI_DIR);
#else
    return QString();
#endif
}
} // namespace

MainWindow::MainWindow(QWidget* parent)
        : QMainWindow(parent),
          updateClient_(new UpdateClient(this)) {
    setWindowFlags(Qt::FramelessWindowHint | Qt::Window);
    setAttribute(Qt::WA_NativeWindow);
    setAttribute(Qt::WA_NoSystemBackground);
    setMinimumSize(1040, 640);
    resize(1180, 720);
    setWindowTitle(QStringLiteral("Mizulune Launcher"));

    connect(updateClient_, &UpdateClient::checkStarted, this, [this] {
        postEvent(QStringLiteral("update.checkStarted"));
    });
    connect(updateClient_, &UpdateClient::checkFailed, this, [this](const QString& error) {
        postEvent(QStringLiteral("update.checkFailed"), {{"error", error}});
    });
    connect(updateClient_, &UpdateClient::latestReleaseChanged, this, &MainWindow::sendReleaseInfo);
    connect(updateClient_, &UpdateClient::downloadStarted, this, [this](const QString& path) {
        postEvent(QStringLiteral("update.downloadStarted"), {{"path", path}});
    });
    connect(updateClient_, &UpdateClient::downloadProgress, this, [this](qint64 received, qint64 total) {
        postEvent(QStringLiteral("update.downloadProgress"), {
            {"received", QString::number(received)},
            {"total", QString::number(total)}
        });
    });
    connect(updateClient_, &UpdateClient::downloadFinished, this, [this](const QString& path) {
        postEvent(QStringLiteral("update.downloadFinished"), {{"path", path}});
    });
    connect(updateClient_, &UpdateClient::downloadFailed, this, [this](const QString& error) {
        postEvent(QStringLiteral("update.downloadFailed"), {{"error", error}});
    });
}

MainWindow::~MainWindow() {
    if (webView_) {
        webView_->remove_WebMessageReceived(messageToken_);
    }
}

void MainWindow::initializeWebView() {
    if (webController_) return;

    const QString userData = QDir(ProfileStore::profileDirectory()).filePath(QStringLiteral("webview2"));
    QDir().mkpath(userData);

    HRESULT hr = CreateCoreWebView2EnvironmentWithOptions(
        nullptr,
        toWide(userData).c_str(),
        nullptr,
        Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>(
            [this](HRESULT result, ICoreWebView2Environment* environment) -> HRESULT {
                if (FAILED(result) || !environment) {
                    QMessageBox::critical(this,
                        QStringLiteral("WebView2"),
                        QStringLiteral("WebView2 初始化失败。请安装 Microsoft Edge WebView2 Runtime。"));
                    return S_OK;
                }

                webEnvironment_ = environment;
                environment->CreateCoreWebView2Controller(
                    reinterpret_cast<HWND>(winId()),
                    Callback<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler>(
                        [this](HRESULT controllerResult, ICoreWebView2Controller* controller) -> HRESULT {
                            if (FAILED(controllerResult) || !controller) {
                                QMessageBox::critical(this,
                                    QStringLiteral("WebView2"),
                                    QStringLiteral("WebView2 Controller 创建失败。"));
                                return S_OK;
                            }

                            webController_ = controller;
                            webController_->get_CoreWebView2(&webView_);
                            resizeWebView();

                            Microsoft::WRL::ComPtr<ICoreWebView2Controller2> controller2;
                            if (SUCCEEDED(webController_->QueryInterface(IID_PPV_ARGS(&controller2))) && controller2) {
                                COREWEBVIEW2_COLOR transparentColor{0, 0, 0, 0};
                                controller2->put_DefaultBackgroundColor(transparentColor);
                            }

                            Microsoft::WRL::ComPtr<ICoreWebView2Settings> settings;
                            if (SUCCEEDED(webView_->get_Settings(&settings)) && settings) {
                                settings->put_AreDevToolsEnabled(TRUE);
                                settings->put_IsStatusBarEnabled(FALSE);
                            }

                            webView_->add_WebMessageReceived(
                                Callback<ICoreWebView2WebMessageReceivedEventHandler>(
                                    [this](ICoreWebView2*, ICoreWebView2WebMessageReceivedEventArgs* args) -> HRESULT {
                                        LPWSTR raw = nullptr;
                                        if (SUCCEEDED(args->get_WebMessageAsJson(&raw)) && raw) {
                                            handleWebMessage(QString::fromWCharArray(raw));
                                            CoTaskMemFree(raw);
                                        }
                                        return S_OK;
                                    }).Get(),
                                &messageToken_);

                            navigateToWebUi();
                            return S_OK;
                        }).Get());
                return S_OK;
            }).Get());

    if (FAILED(hr)) {
        QMessageBox::critical(this,
            QStringLiteral("WebView2"),
            QStringLiteral("WebView2Loader 调用失败。请确认 WebView2 Runtime 已安装。"));
    }
}

void MainWindow::navigateToWebUi() {
    if (!webView_) return;
    const QString index = webUiIndexPath();
    const QString url = QFileInfo(index).isFile()
            ? QUrl::fromLocalFile(index).toString(QUrl::FullyEncoded)
            : QStringLiteral("data:text/html,<h1>Mizulune webui missing</h1>");
    webView_->Navigate(toWide(url).c_str());
}

namespace {
QString extractWebUiResources() {
    QString tempPath = QStandardPaths::writableLocation(QStandardPaths::TempLocation) + QStringLiteral("/mizulune_webui");
    QDir().mkpath(tempPath);
    QDir().mkpath(tempPath + QStringLiteral("/res"));

    QStringList files = {
        QStringLiteral("webui/index.html"),
        QStringLiteral("webui/styles.css"),
        QStringLiteral("webui/app.js"),
        QStringLiteral("webui/res/bg.png"),
        QStringLiteral("webui/res/logo.png"),
        QStringLiteral("webui/res/logo_text.png")
    };

    for (const QString& relPath : files) {
        QString qrcPath = QStringLiteral(":/") + relPath;
        QString targetPath = tempPath + QStringLiteral("/") + relPath.mid(6); // remove "webui/" prefix
        
        if (QFile::exists(targetPath)) {
            QFile::remove(targetPath);
        }
        QFile::copy(qrcPath, targetPath);
    }
    return tempPath + QStringLiteral("/index.html");
}
} // namespace

QString MainWindow::webUiIndexPath() const {
    const QString dist = QDir(QCoreApplication::applicationDirPath())
            .filePath(QStringLiteral("webui/index.html"));
    if (QFileInfo(dist).isFile()) return dist;

    const QString src = sourceWebUiDir();
    if (!src.isEmpty()) {
        const QString path = QDir(src).filePath(QStringLiteral("index.html"));
        if (QFileInfo(path).isFile()) return path;
    }
    return extractWebUiResources();
}

void MainWindow::resizeWebView() {
    if (!webController_) return;
    const double scale = devicePixelRatioF();
    RECT bounds{
        0,
        0,
        static_cast<LONG>(std::ceil(width() * scale)),
        static_cast<LONG>(std::ceil(height() * scale))
    };
    webController_->put_Bounds(bounds);
}

void MainWindow::handleWebMessage(const QString& jsonText) {
    const QJsonDocument doc = QJsonDocument::fromJson(jsonText.toUtf8());
    if (!doc.isObject()) {
        postError(QStringLiteral("Invalid web message."));
        return;
    }
    const QJsonObject root = doc.object();
    const QString type = root.value(QStringLiteral("type")).toString();
    const QJsonObject payload = root.value(QStringLiteral("payload")).toObject();

    if (type == QStringLiteral("ready")) {
        webReady_ = true;
        sendProfile();
        sendInstances();
        updateClient_->checkLatestRelease();
    } else if (type == QStringLiteral("window.minimize")) {
        showMinimized();
    } else if (type == QStringLiteral("window.close")) {
        close();
    } else if (type == QStringLiteral("window.drag")) {
        ReleaseCapture();
        SendMessageW(reinterpret_cast<HWND>(winId()), WM_NCLBUTTONDOWN, HTCAPTION, 0);
    } else if (type == QStringLiteral("profile.get")) {
        sendProfile();
    } else if (type == QStringLiteral("profile.save")) {
        saveProfile(payload);
    } else if (type == QStringLiteral("instances.scan")) {
        sendInstances();
    } else if (type == QStringLiteral("inject")) {
        startInjection(payload);
    } else if (type == QStringLiteral("update.check")) {
        updateClient_->checkLatestRelease();
    } else if (type == QStringLiteral("update.download")) {
        updateClient_->downloadLatestLoader();
    } else {
        postError(QStringLiteral("Unknown web message: %1").arg(type));
    }
}

void MainWindow::postEvent(const QString& type, const QJsonObject& payload) {
    if (!webView_ || !webReady_) return;
    const QJsonObject root{
        {QStringLiteral("type"), type},
        {QStringLiteral("payload"), payload}
    };
    const QString json = QString::fromUtf8(QJsonDocument(root).toJson(QJsonDocument::Compact));
    webView_->PostWebMessageAsJson(toWide(json).c_str());
}

void MainWindow::postError(const QString& message) {
    postEvent(QStringLiteral("error"), {{"message", message}});
}

void MainWindow::sendProfile() {
    const LoaderProfile profile = ProfileStore::load();
    postEvent(QStringLiteral("profile"), {
        {"displayName", profile.displayName},
        {"closeAfterInjection", profile.closeAfterInjection},
        {"profilePath", ProfileStore::profileFilePath()}
    });
}

void MainWindow::saveProfile(const QJsonObject& payload) {
    LoaderProfile profile = ProfileStore::load();
    profile.displayName = payload.value(QStringLiteral("displayName")).toString(profile.displayName);
    profile.closeAfterInjection = payload.value(QStringLiteral("closeAfterInjection")).toBool(profile.closeAfterInjection);

    QString error;
    if (!ProfileStore::save(profile, &error)) {
        postEvent(QStringLiteral("profile.saveFailed"), {{"error", error}});
        return;
    }
    sendProfile();
    postEvent(QStringLiteral("profile.saved"), {{"path", ProfileStore::profileFilePath()}});
}

void MainWindow::sendInstances() {
    const auto processes = list_java_processes();
    QJsonArray instances;
    for (const auto& jp : processes) {
        if (!isMinecraft(jp.window_title, jp.window_class)) continue;
        QString title = fromW(jp.window_title);
        if (title.isEmpty()) title = QStringLiteral("(starting up - %1)").arg(fromW(jp.window_class));
        instances.append(QJsonObject{
            {"pid", QString::number(jp.pid)},
            {"title", title},
            {"className", fromW(jp.window_class)}
        });
    }
    postEvent(QStringLiteral("instances"), {{"items", instances}});
}

void MainWindow::startInjection(const QJsonObject& payload) {
    if (injectionInFlight_) return;
    bool okPid = false;
    const unsigned long pid = payload.value(QStringLiteral("pid")).toString().toULong(&okPid);
    const QString title = payload.value(QStringLiteral("title")).toString();
    if (!okPid || pid == 0) {
        postError(QStringLiteral("Invalid target process."));
        return;
    }

    injectionInFlight_ = true;
    postEvent(QStringLiteral("inject.started"), {{"pid", QString::number(pid)}, {"title", title}});

    std::thread([this, pid]() {
        const std::wstring error = inject(static_cast<DWORD>(pid));
        const QString qError = fromW(error);
        QMetaObject::invokeMethod(this, [this, qError]() {
            injectionInFlight_ = false;
            const bool ok = qError.isEmpty();
            postEvent(QStringLiteral("inject.finished"), {{"ok", ok}, {"error", qError}});
            if (ok && ProfileStore::load().closeAfterInjection) {
                playExitThenQuit();
            } else {
                sendInstances();
            }
        }, Qt::QueuedConnection);
    }).detach();
}

void MainWindow::sendReleaseInfo() {
    const ReleaseInfo rel = updateClient_->latestRelease();
    postEvent(QStringLiteral("update.release"), {
        {"tagName", rel.tagName},
        {"title", rel.title},
        {"body", rel.body},
        {"htmlUrl", rel.htmlUrl},
        {"assetName", rel.assetName},
        {"assetRevision", rel.assetRevision},
        {"hasLoaderAsset", rel.hasLoaderAsset},
        {"updateAvailable", rel.updateAvailable},
        {"currentBuild", currentBuildLabel()}
    });
}

QString MainWindow::currentBuildLabel() const {
    const QString rev = UpdateClient::currentRevision();
    return rev.isEmpty() ? QStringLiteral("local build") : QStringLiteral("build %1").arg(rev);
}

void MainWindow::setDwmFrame() {
    HWND hwnd = reinterpret_cast<HWND>(winId());
    if (!hwnd) return;
    int cornerPref = DWMWCP_ROUND;
    DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, &cornerPref, sizeof(cornerPref));
    int backdrop = DWMSBT_TRANSIENTWINDOW;
    DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, &backdrop, sizeof(backdrop));
}

void MainWindow::playEntrance() {
    show();
    setDwmFrame();
    initializeWebView();
    raise();
    activateWindow();

    if (windowOpacity() < 0.01) {
        auto* fade = new QPropertyAnimation(this, "windowOpacity", this);
        fade->setDuration(220);
        fade->setStartValue(0.0);
        fade->setEndValue(1.0);
        fade->start(QAbstractAnimation::DeleteWhenStopped);
    }
}

void MainWindow::mousePressEvent(QMouseEvent* event) {
    if (event->button() == Qt::LeftButton) {
        if (auto* handle = windowHandle()) {
            handle->startSystemMove();
            event->accept();
            return;
        }
    }
    QMainWindow::mousePressEvent(event);
}

void MainWindow::resizeEvent(QResizeEvent* event) {
    QMainWindow::resizeEvent(event);
    resizeWebView();
}

void MainWindow::closeEvent(QCloseEvent* event) {
    if (exiting_) {
        QMainWindow::closeEvent(event);
        return;
    }
    event->ignore();
    playExitThenQuit();
}

void MainWindow::playExitThenQuit() {
    if (exiting_) return;
    exiting_ = true;

    auto* fade = new QPropertyAnimation(this, "windowOpacity", this);
    fade->setDuration(180);
    fade->setStartValue(windowOpacity());
    fade->setEndValue(0.0);
    connect(fade, &QAbstractAnimation::finished, qApp, &QApplication::quit);
    fade->start(QAbstractAnimation::DeleteWhenStopped);
}

} // namespace loader
