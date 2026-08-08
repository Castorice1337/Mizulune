#include "BackendProcessBridge.h"

#include "ProfileStore.h"

#include <QCoreApplication>
#include <QDir>
#include <QElapsedTimer>
#include <QFile>
#include <QFileInfo>
#include <QJsonDocument>
#include <QLocalSocket>
#include <QProcessEnvironment>
#include <QThread>
#include <QUuid>
#include <QTimer>

#include <windows.h>

namespace loader {

BackendProcessBridge::BackendProcessBridge(QObject* parent)
        : QObject(parent), process_(new QProcess(this)), socket_(new QLocalSocket(this)) {
    process_->setProcessChannelMode(QProcess::SeparateChannels);
    process_->setCreateProcessArgumentsModifier([](QProcess::CreateProcessArguments* args) {
        args->flags |= CREATE_NO_WINDOW;
    });
    connect(process_, &QProcess::readyReadStandardOutput, this, &BackendProcessBridge::readProcessLog);
    connect(process_, &QProcess::readyReadStandardError, this, &BackendProcessBridge::readProcessLog);
    connect(socket_, &QLocalSocket::readyRead, this, &BackendProcessBridge::readControlChannel);
    connect(process_, &QProcess::errorOccurred, this, [this](QProcess::ProcessError) {
        if (shuttingDown_) return;
        const QString message = process_->errorString();
        failPending(QStringLiteral("host_unavailable"), message);
        emit hostUnavailable(message);
    });
    connect(process_, qOverload<int, QProcess::ExitStatus>(&QProcess::finished), this,
            [this](int exitCode, QProcess::ExitStatus status) {
        if (shuttingDown_) return;
        const QString bootstrapLog = QDir(ProfileStore::profileDirectory())
                .filePath(QStringLiteral("logs/fantnel-host-bootstrap.log"));
        const QString message = status == QProcess::CrashExit
                ? QStringLiteral("Fantnel host crashed (exit code %1). Log: %2").arg(exitCode).arg(bootstrapLog)
                : QStringLiteral("Fantnel host exited with code %1. Log: %2").arg(exitCode).arg(bootstrapLog);
        failPending(QStringLiteral("host_stopped"), message);
        emit hostUnavailable(message);
    });
}

BackendProcessBridge::~BackendProcessBridge() {
    shutdown();
}

QString BackendProcessBridge::request(const QString& method,
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
                {QStringLiteral("message"), QStringLiteral("The backend request id is already pending.")}
            }}
        };
        if (callback) callback(response);
        emit responseReady(response);
        return id;
    }

    callbacks_.insert(id, std::move(callback));
    const int timeoutMs = method == QStringLiteral("launch.start") || method == QStringLiteral("proxy.start")
            ? 10 * 60 * 1000 : 90 * 1000;
    QTimer::singleShot(timeoutMs, this, [this, id] {
        if (!callbacks_.contains(id)) return;
        const auto pending = callbacks_.take(id);
        const QJsonObject response{
            {QStringLiteral("id"), id},
            {QStringLiteral("ok"), false},
            {QStringLiteral("error"), QJsonObject{
                {QStringLiteral("code"), QStringLiteral("host_timeout")},
                {QStringLiteral("message"), QStringLiteral("The Fantnel host did not respond in time.")}
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
    if (socket_->write(line) != line.size()) {
        const QJsonObject response{
            {QStringLiteral("id"), id},
            {QStringLiteral("ok"), false},
            {QStringLiteral("error"), QJsonObject{
                {QStringLiteral("code"), QStringLiteral("host_write_failed")},
                {QStringLiteral("message"), QStringLiteral("Failed to send the Fantnel command.")}
            }}
        };
        const auto pending = callbacks_.take(id);
        if (pending) pending(response);
        emit responseReady(response);
    }
    return id;
}

bool BackendProcessBridge::isRunning() const {
    return process_->state() != QProcess::NotRunning && socket_->state() == QLocalSocket::ConnectedState;
}

void BackendProcessBridge::shutdown() {
    if (!process_ || process_->state() == QProcess::NotRunning) return;
    shuttingDown_ = true;
    if (socket_->state() == QLocalSocket::ConnectedState) {
        request(QStringLiteral("host.shutdown"));
        socket_->waitForBytesWritten(500);
        socket_->disconnectFromServer();
    }
    if (!process_->waitForFinished(2500)) {
        process_->terminate();
        if (!process_->waitForFinished(1000)) process_->kill();
    }
    failPending(QStringLiteral("host_stopped"), QStringLiteral("Fantnel host stopped."));
}

bool BackendProcessBridge::ensureStarted(QString& error) {
    if (isRunning()) return true;
    if (process_->state() != QProcess::NotRunning) {
        process_->kill();
        process_->waitForFinished(1000);
    }
    socket_->abort();

    const QString packaged = packagedHostPath();
    if (!QFileInfo::exists(packaged)) {
        error = QStringLiteral("Fantnel host not found: %1").arg(packaged);
        return false;
    }
    const QString overridePath = qEnvironmentVariable("MIZULUNE_FANTNEL_HOST_PATH");
    const QString working = runtimeDirectory();
    QDir().mkpath(working);
    QString executable = packaged;
    if (overridePath.isEmpty()) {
        if (!stageRuntime(QFileInfo(packaged).absolutePath(), working, error)) return false;
        executable = QDir(working).filePath(QStringLiteral("Mizulune.FantnelHost.exe"));
    }

    const QString pipeName = QStringLiteral("MizuluneFantnel-%1-%2")
            .arg(QCoreApplication::applicationPid())
            .arg(QUuid::createUuid().toString(QUuid::Id128));
    process_->setWorkingDirectory(working);
    process_->setProgram(executable);
    process_->setArguments({
        QStringLiteral("--pipe"), pipeName,
        QStringLiteral("--parent-pid"), QString::number(QCoreApplication::applicationPid()),
        QStringLiteral("--state-dir"), working,
        QStringLiteral("--protocol-dir"), ProfileStore::profileDirectory()
    });
    shuttingDown_ = false;
    process_->start();
    if (!process_->waitForStarted(5000)) {
        error = process_->errorString();
        return false;
    }
    // On Windows QLocalSocket does not retry ERROR_FILE_NOT_FOUND. A freshly
    // staged self-contained .NET host may need several seconds for extraction
    // or antivirus inspection before it creates the pipe, so retry explicitly.
    constexpr qint64 startupTimeoutMs = 90 * 1000;
    QElapsedTimer startupTimer;
    startupTimer.start();
    QString lastSocketError;
    while (startupTimer.elapsed() < startupTimeoutMs) {
        socket_->abort();
        socket_->connectToServer(pipeName, QIODevice::ReadWrite);
        if (socket_->state() == QLocalSocket::ConnectedState || socket_->waitForConnected(250)) {
            return true;
        }
        lastSocketError = socket_->errorString();
        shuttingDown_ = true;
        const bool exitedBeforePipe = process_->waitForFinished(0);
        shuttingDown_ = false;
        if (exitedBeforePipe) {
            error = QStringLiteral("Fantnel host exited with code %1 before creating its control pipe. Log: %2")
                    .arg(process_->exitCode())
                    .arg(QDir(ProfileStore::profileDirectory())
                                 .filePath(QStringLiteral("logs/fantnel-host-bootstrap.log")));
            return false;
        }
        QThread::msleep(100);
    }

    error = QStringLiteral("Timed out waiting for the Fantnel control pipe after %1 seconds: %2")
            .arg(startupTimeoutMs / 1000)
            .arg(lastSocketError);
    shuttingDown_ = true;
    if (process_->state() != QProcess::NotRunning) {
        process_->kill();
        process_->waitForFinished(1000);
    }
    shuttingDown_ = false;
    return false;
}

QString BackendProcessBridge::packagedHostPath() const {
    const QString overridePath = qEnvironmentVariable("MIZULUNE_FANTNEL_HOST_PATH");
    if (!overridePath.isEmpty()) return QDir::cleanPath(overridePath);
    return QDir(QCoreApplication::applicationDirPath())
            .filePath(QStringLiteral("fantnel/Mizulune.FantnelHost.exe"));
}

QString BackendProcessBridge::runtimeDirectory() const {
    return QDir(ProfileStore::profileDirectory()).filePath(QStringLiteral("backends/fantnel"));
}

bool BackendProcessBridge::stageRuntime(const QString& sourceDirectory,
                                        const QString& targetDirectory,
                                        QString& error) const {
    QDir source(sourceDirectory);
    if (!source.exists()) {
        error = QStringLiteral("Fantnel package directory is missing: %1").arg(sourceDirectory);
        return false;
    }
    QDir().mkpath(targetDirectory);
    const auto entries = source.entryInfoList(QDir::Files | QDir::Dirs | QDir::NoDotAndDotDot);
    for (const QFileInfo& entry : entries) {
        const QString target = QDir(targetDirectory).filePath(entry.fileName());
        if (entry.isDir()) {
            if (!stageRuntime(entry.absoluteFilePath(), target, error)) return false;
            continue;
        }
        const QFileInfo targetInfo(target);
        if (targetInfo.exists() && targetInfo.size() == entry.size()
                && targetInfo.lastModified() >= entry.lastModified()) continue;
        QFile::remove(target);
        if (!QFile::copy(entry.absoluteFilePath(), target)) {
            error = QStringLiteral("Failed to stage Fantnel runtime file: %1").arg(entry.fileName());
            return false;
        }
    }
    return true;
}

void BackendProcessBridge::readProcessLog() {
    const QByteArray data = process_->readAllStandardOutput() + process_->readAllStandardError();
    if (data.isEmpty()) return;
    const QString logDir = QDir(ProfileStore::profileDirectory()).filePath(QStringLiteral("logs"));
    QDir().mkpath(logDir);
    QFile file(QDir(logDir).filePath(QStringLiteral("fantnel-host.log")));
    if (file.open(QIODevice::WriteOnly | QIODevice::Append)) file.write(data);
}

void BackendProcessBridge::readControlChannel() {
    outputBuffer_.append(socket_->readAll());
    while (true) {
        const qsizetype newline = outputBuffer_.indexOf('\n');
        if (newline < 0) break;
        const QByteArray line = outputBuffer_.left(newline).trimmed();
        outputBuffer_.remove(0, newline + 1);
        if (!line.isEmpty()) handleLine(line);
    }
}

void BackendProcessBridge::handleLine(const QByteArray& line) {
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

void BackendProcessBridge::failPending(const QString& code, const QString& message) {
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
