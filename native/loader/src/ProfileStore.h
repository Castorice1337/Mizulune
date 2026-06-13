#pragma once

#include <QString>

namespace loader {

struct LoaderProfile {
    QString displayName;
    bool closeAfterInjection = false;
};

class ProfileStore {
public:
    static QString profileDirectory();
    static QString profileFilePath();
    static QString updatesDirectory();
    static QString defaultDisplayName();
    static QString sanitizeDisplayName(QString value);

    static LoaderProfile load();
    static bool save(const LoaderProfile& profile, QString* error = nullptr);
    static bool saveDisplayName(const QString& displayName, QString* error = nullptr);
    static bool ensureDirectories(QString* error = nullptr);
};

} // namespace loader
