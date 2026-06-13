#include "MainWindow.h"
#include "ProfileStore.h"

#include <QApplication>
#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QStyleFactory>
#include <QTextStream>

namespace {
void logStartupDebug() {
    QDir().mkpath(loader::ProfileStore::profileDirectory());

    QFile file(QDir(loader::ProfileStore::profileDirectory()).filePath(QStringLiteral("loader_debug.txt")));
    if (!file.open(QIODevice::WriteOnly | QIODevice::Text)) return;

    QTextStream out(&file);
    out << "Mizulune WebView2 Loader Debug\n";
    out << "==============================\n\n";
    out << "applicationDir = " << QCoreApplication::applicationDirPath() << "\n";
    out << "profileDir     = " << loader::ProfileStore::profileDirectory() << "\n";
    out << "webui/index    = "
        << QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("webui/index.html"))
        << "\n";
    out << "res/bg         = "
        << QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("res/bg.png"))
        << "\n";
}
} // namespace

int main(int argc, char** argv) {
    QApplication::setHighDpiScaleFactorRoundingPolicy(
        Qt::HighDpiScaleFactorRoundingPolicy::PassThrough);

    QApplication app(argc, argv);
    QApplication::setStyle(QStyleFactory::create(QStringLiteral("Fusion")));
    QApplication::setApplicationName(QStringLiteral("Mizulune Client"));
    QApplication::setOrganizationName(QStringLiteral("Mizulune"));

    logStartupDebug();

    auto* main = new loader::MainWindow();
    main->setWindowOpacity(0.0);
    main->playEntrance();

    int rc = app.exec();
    delete main;
    return rc;
}
