#include "SdkProcessBridge.h"

#include "ProfileStore.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonDocument>
#include <QProcessEnvironment>
#include <QTimer>

#include <windows.h>

namespace loader {

SdkProcessBridge::SdkProcessBridge(QObject* parent)
        : QObject(parent), process_(new QProcess(this)) {
    process_->setProcessChannelMode(QProcess::SeparateChannels);
    process_->setCreateProcessArgumentsModifier([](QProcess::CreateProcessArguments* args) {
        args->flags |= CREATE_NO_WINDOW;
    });
    connect(process_, &QProcess::readyReadStandardOutput, this, &SdkProcessBridge::readStandardOutput);
    connect(process_, &QProcess::readyReadStandardError, this, &SdkProcessBridge::readStandardError);
    connect(process_, &QProcess::errorOccurred, this, [this](QProcess::ProcessError) {
        if (shuttingDown_) return;
        const QString message = process_->errorString();
        failPending(QStringLiteral("host_unavailable"), message);
        emit hostUnavailable(message);
    });
    connect(process_, qOverload<int, QProcess::ExitStatus>(&QProcess::finished), this,
            [this](int exitCode, QProcess::ExitStatus status) {
        if (shuttingDown_) return;
        const QString message = status == QProcess::CrashExit
                ? QStringLiteral("OpenSDK host crashed.")
                : QStringLiteral("OpenSDK host exited with code %1.").arg(exitCode);
        failPending(QStringLiteral("host_stopped"), message);
        emit hostUnavailable(message);
    });
}

SdkProcessBridge::~SdkProcessBridge() {
    shutdown();
}

QString SdkProcessBridge::request(const QString& method,
                                  const QJsonObject& parameters,
                                  Callback callback,
                                  const QString& requestId) {
    const QString id = requestId.isEmpty()
            ? QStringLiteral("native-%1").arg(nextRequestId_++)
            : requestId;
    QString error;
    if (!ensureStarted(error)) {
        const QJsonObject response{
            {QStringLiteral("id"), id},
            {QStringLiteral("ok"), false},
            {QStringLiteral("error"), QJsonObject{
                {QStringLiteral("code"), QStringLiteral("host_unavailable")},
                {QStringLiteral("message"), error}
            }}
        };
        if (callback) callback(response);
        emit responseReady(response);
        return id;
    }

    if (callbacks_.contains(id)) {
        const QJsonObject response{
            {QStringLiteral("id"), id},
            {QStringLiteral("ok"), false},
            {QStringLiteral("error"), QJsonObject{
                {QStringLiteral("code"), QStringLiteral("duplicate_request")},
                {QStringLiteral("message"), QStringLiteral("The SDK request id is already pending.")}
            }}
        };
        if (callback) callback(response);
        emit responseReady(response);
        return id;
    }

    callbacks_.insert(id, std::move(callback));
    QTimer::singleShot(60000, this, [this, id] {
        if (!callbacks_.contains(id)) return;
        const auto pending = callbacks_.take(id);
        const QJsonObject response{
            {QStringLiteral("id"), id},
            {QStringLiteral("ok"), false},
            {QStringLiteral("error"), QJsonObject{
                {QStringLiteral("code"), QStringLiteral("host_timeout")},
                {QStringLiteral("message"), QStringLiteral("The OpenSDK host did not respond in time.")}
            }}
        };
        if (pending) pending(response);
        emit responseReady(response);
    });
    const QJsonObject message{
        {QStringLiteral("id"), id},
        {QStringLiteral("method"), method},
        {QStringLiteral("params"), parameters}
    };
    const QByteArray line = QJsonDocument(message).toJson(QJsonDocument::Compact) + '\n';
    if (process_->write(line) != line.size()) {
        const QJsonObject response{
            {QStringLiteral("id"), id},
            {QStringLiteral("ok"), false},
            {QStringLiteral("error"), QJsonObject{
                {QStringLiteral("code"), QStringLiteral("host_write_failed")},
                {QStringLiteral("message"), QStringLiteral("Failed to send the SDK command.")}
            }}
        };
        const auto pending = callbacks_.take(id);
        if (pending) pending(response);
        emit responseReady(response);
    }
    return id;
}

bool SdkProcessBridge::isRunning() const {
    return process_->state() != QProcess::NotRunning;
}

void SdkProcessBridge::shutdown() {
    if (!process_ || process_->state() == QProcess::NotRunning) return;
    shuttingDown_ = true;
    request(QStringLiteral("proxy.stop"));
    process_->waitForBytesWritten(500);
    process_->closeWriteChannel();
    if (!process_->waitForFinished(2500)) {
        process_->terminate();
        if (!process_->waitForFinished(1000)) process_->kill();
    }
    failPending(QStringLiteral("host_stopped"), QStringLiteral("OpenSDK host stopped."));
}

bool SdkProcessBridge::ensureStarted(QString& error) {
    if (process_->state() != QProcess::NotRunning) return true;
    const QString executable = hostPath();
    if (!QFileInfo::exists(executable)) {
        error = QStringLiteral("OpenSDK host not found: %1").arg(executable);
        return false;
    }
    const QString working = QDir(ProfileStore::profileDirectory()).filePath(QStringLiteral("sdk"));
    QDir().mkpath(working);
    process_->setWorkingDirectory(working);
    process_->setProgram(executable);
    process_->setArguments({});
    process_->start();
    if (!process_->waitForStarted(5000)) {
        error = process_->errorString();
        return false;
    }
    return true;
}

QString SdkProcessBridge::hostPath() const {
    const QString overridePath = qEnvironmentVariable("MIZULUNE_SDK_HOST_PATH");
    if (!overridePath.isEmpty()) return QDir::cleanPath(overridePath);
    return QDir(QCoreApplication::applicationDirPath())
            .filePath(QStringLiteral("sdk/Mizulune.SdkHost.exe"));
}

void SdkProcessBridge::readStandardOutput() {
    outputBuffer_.append(process_->readAllStandardOutput());
    while (true) {
        const qsizetype newline = outputBuffer_.indexOf('\n');
        if (newline < 0) break;
        const QByteArray line = outputBuffer_.left(newline).trimmed();
        outputBuffer_.remove(0, newline + 1);
        if (!line.isEmpty()) handleLine(line);
    }
}

void SdkProcessBridge::readStandardError() {
    const QByteArray data = process_->readAllStandardError();
    if (data.isEmpty()) return;
    const QString logDir = QDir(ProfileStore::profileDirectory()).filePath(QStringLiteral("logs"));
    QDir().mkpath(logDir);
    QFile file(QDir(logDir).filePath(QStringLiteral("sdk-host.log")));
    if (file.open(QIODevice::WriteOnly | QIODevice::Append)) file.write(data);
}

void SdkProcessBridge::handleLine(const QByteArray& line) {
    QJsonParseError parseError{};
    const QJsonDocument document = QJsonDocument::fromJson(line, &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) return;
    const QJsonObject object = document.object();
    if (object.contains(QStringLiteral("event"))) {
        emit eventReady(object);
        return;
    }
    const QString id = object.value(QStringLiteral("id")).toString();
    const auto callback = callbacks_.take(id);
    if (callback) callback(object);
    emit responseReady(object);
}

void SdkProcessBridge::failPending(const QString& code, const QString& message) {
    const auto ids = callbacks_.keys();
    for (const QString& id : ids) {
        const QJsonObject response{
            {QStringLiteral("id"), id},
            {QStringLiteral("ok"), false},
            {QStringLiteral("error"), QJsonObject{
                {QStringLiteral("code"), code},
                {QStringLiteral("message"), message}
            }}
        };
        const auto callback = callbacks_.take(id);
        if (callback) callback(response);
        emit responseReady(response);
    }
}

} // namespace loader
