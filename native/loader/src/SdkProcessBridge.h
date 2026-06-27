#pragma once

#include <QHash>
#include <QJsonObject>
#include <QObject>
#include <QProcess>

#include <functional>

namespace loader {

class SdkProcessBridge final : public QObject {
    Q_OBJECT

public:
    using Callback = std::function<void(const QJsonObject&)>;

    explicit SdkProcessBridge(QObject* parent = nullptr);
    ~SdkProcessBridge() override;

    QString request(const QString& method,
                    const QJsonObject& parameters = {},
                    Callback callback = {},
                    const QString& requestId = {});
    bool isRunning() const;
    void shutdown();

signals:
    void responseReady(const QJsonObject& response);
    void eventReady(const QJsonObject& event);
    void hostUnavailable(const QString& error);

private:
    bool ensureStarted(QString& error);
    QString hostPath() const;
    void readStandardOutput();
    void readStandardError();
    void handleLine(const QByteArray& line);
    void failPending(const QString& code, const QString& message);

    QProcess* process_ = nullptr;
    QByteArray outputBuffer_;
    QHash<QString, Callback> callbacks_;
    quint64 nextRequestId_ = 1;
    bool shuttingDown_ = false;
};

} // namespace loader
