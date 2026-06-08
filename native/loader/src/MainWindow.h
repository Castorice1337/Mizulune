#pragma once

#include <QMainWindow>
#include <QJsonObject>

#include <wrl.h>
#include <WebView2.h>

class QCloseEvent;
class QMouseEvent;
class QPropertyAnimation;
class QResizeEvent;

namespace loader {

class UpdateClient;

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit MainWindow(QWidget* parent = nullptr);
    ~MainWindow() override;

    void playEntrance();
    void playExitThenQuit();

protected:
    void closeEvent(QCloseEvent*) override;
    void mousePressEvent(QMouseEvent*) override;
    void resizeEvent(QResizeEvent*) override;

private:
    void initializeWebView();
    void navigateToWebUi();
    void resizeWebView();
    void handleWebMessage(const QString& jsonText);
    void postEvent(const QString& type, const QJsonObject& payload = {});
    void postError(const QString& message);
    void sendProfile();
    void saveProfile(const QJsonObject& payload);
    void sendInstances();
    void startInjection(const QJsonObject& payload);
    void sendReleaseInfo();
    void setDwmFrame();
    QString webUiIndexPath() const;
    QString webUiFallbackIndexPath() const;
    QString currentBuildLabel() const;

    Microsoft::WRL::ComPtr<ICoreWebView2Environment> webEnvironment_;
    Microsoft::WRL::ComPtr<ICoreWebView2Controller> webController_;
    Microsoft::WRL::ComPtr<ICoreWebView2> webView_;
    EventRegistrationToken messageToken_{};

    UpdateClient* updateClient_ = nullptr;
    bool webReady_ = false;
    bool injectionInFlight_ = false;
    bool exiting_ = false;
};

} // namespace loader
