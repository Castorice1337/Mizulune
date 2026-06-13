#pragma once

#include <QObject>
#include <QString>
#include <atomic>
#include <memory>

class QFile;

namespace loader {

struct ReleaseInfo {
    QString tagName;
    QString title;
    QString body;
    QString htmlUrl;
    QString assetName;
    QString assetRevision;
    QString assetDownloadUrl;
    bool hasLoaderAsset = false;
    bool updateAvailable = false;
};

class UpdateClient : public QObject {
    Q_OBJECT
public:
    explicit UpdateClient(QObject* parent = nullptr);
    ~UpdateClient() override;

    void setRepository(const QString& owner, const QString& repo);
    ReleaseInfo latestRelease() const;
    QString downloadPath() const;

    static QString currentRevision();

public slots:
    void checkLatestRelease();
    void downloadLatestLoader();
    void cancelDownload();

signals:
    void checkStarted();
    void checkFailed(const QString& error);
    void latestReleaseChanged();
    void downloadStarted(const QString& path);
    void downloadProgress(qint64 bytesReceived, qint64 bytesTotal);
    void downloadFinished(const QString& path);
    void downloadFailed(const QString& error);

private:
    void setCheckInFlight(bool inFlight);
    void setDownloadInFlight(bool inFlight);

    QString owner_ = QStringLiteral("Castorice1337");
    QString repo_ = QStringLiteral("Mizulune");
    ReleaseInfo latest_;
    QString downloadPath_;
    QString tempDownloadPath_;
    std::atomic_bool checkInFlight_ = false;
    std::atomic_bool downloadInFlight_ = false;
    std::shared_ptr<std::atomic_bool> cancelDownload_;
};

} // namespace loader
