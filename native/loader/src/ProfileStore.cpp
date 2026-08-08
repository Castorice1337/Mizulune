#include "ProfileStore.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QIODevice>
#include <QRegularExpression>
#include <QSaveFile>
#include <QStandardPaths>
#include <QTextStream>

namespace loader {

namespace {
constexpr int kMaxDisplayNameLength = 32;

QString escapePropertyValue(const QString& value) {
    QString out;
    out.reserve(value.size());
    for (QChar ch : value) {
        if (ch == u'\\') {
            out += QStringLiteral("\\\\");
        } else if (ch == u'\n' || ch == u'\r') {
            out += u' ';
        } else {
            out += ch;
        }
    }
    return out;
}

QString unescapePropertyValue(QString value) {
    value.replace(QStringLiteral("\\\\"), QStringLiteral("\\"));
    return value;
}

QString homePath() {
    QString path = QStandardPaths::writableLocation(QStandardPaths::HomeLocation);
    if (path.isEmpty()) path = QDir::homePath();
    return path;
}
} // namespace

QString ProfileStore::profileDirectory() {
    return QDir(homePath()).filePath(QStringLiteral(".mizulune"));
}

QString ProfileStore::profileFilePath() {
    return QDir(profileDirectory()).filePath(QStringLiteral("loader-profile.properties"));
}

QString ProfileStore::updatesDirectory() {
    return QDir(profileDirectory()).filePath(QStringLiteral("updates"));
}

QString ProfileStore::maxHookRuntimePath() {
    return QDir(profileDirectory())
            .filePath(QStringLiteral("runtime/maxhook/MaxHook.dll"));
}

QString ProfileStore::defaultDisplayName() {
    QString user = qEnvironmentVariable("USERNAME");
    if (user.isEmpty()) user = qEnvironmentVariable("USER");
    if (user.isEmpty()) user = QStringLiteral("Player");
    return sanitizeDisplayName(user);
}

QString ProfileStore::sanitizeDisplayName(QString value) {
    value = value.trimmed();
    value.replace(QRegularExpression(QStringLiteral("[\\r\\n\\t]+")), QStringLiteral(" "));
    value.replace(QRegularExpression(QStringLiteral("\\s{2,}")), QStringLiteral(" "));
    if (value.size() > kMaxDisplayNameLength) {
        value = value.left(kMaxDisplayNameLength).trimmed();
    }
    return value;
}

LoaderProfile ProfileStore::load() {
    LoaderProfile profile;
    profile.displayName = defaultDisplayName();

    QFile file(profileFilePath());
    if (!file.open(QIODevice::ReadOnly | QIODevice::Text)) {
        return profile;
    }

    QTextStream in(&file);
    in.setEncoding(QStringConverter::Utf8);
    while (!in.atEnd()) {
        QString line = in.readLine().trimmed();
        if (line.isEmpty() || line.startsWith(u'#')) continue;
        const qsizetype eq = line.indexOf(u'=');
        if (eq <= 0) continue;
        const QString key = line.left(eq).trimmed();
        const QString value = unescapePropertyValue(line.mid(eq + 1).trimmed());
        if (key == QStringLiteral("displayName")) {
            const QString sanitized = sanitizeDisplayName(value);
            if (!sanitized.isEmpty()) profile.displayName = sanitized;
        } else if (key == QStringLiteral("closeAfterInjection")) {
            profile.closeAfterInjection = (value == QStringLiteral("true"));
        }
    }
    return profile;
}

bool ProfileStore::save(const LoaderProfile& profile, QString* error) {
    if (!ensureDirectories(error)) return false;

    const QString sanitized = sanitizeDisplayName(profile.displayName);
    if (sanitized.isEmpty()) {
        if (error) *error = QStringLiteral("Display name is empty.");
        return false;
    }

    QFile file(profileFilePath());
    if (!file.open(QIODevice::WriteOnly | QIODevice::Text | QIODevice::Truncate)) {
        if (error) *error = file.errorString();
        return false;
    }

    QTextStream out(&file);
    out.setEncoding(QStringConverter::Utf8);
    out << "# Mizulune loader profile\n";
    out << "displayName=" << escapePropertyValue(sanitized) << "\n";
    out << "closeAfterInjection=" << (profile.closeAfterInjection ? "true" : "false") << "\n";
    return true;
}

bool ProfileStore::saveDisplayName(const QString& displayName, QString* error) {
    LoaderProfile profile = load();
    profile.displayName = displayName;
    return save(profile, error);
}

bool ProfileStore::ensureDirectories(QString* error) {
    QDir dir;
    if (!dir.mkpath(profileDirectory())) {
        if (error) *error = QStringLiteral("Failed to create profile directory.");
        return false;
    }
    if (!dir.mkpath(updatesDirectory())) {
        if (error) *error = QStringLiteral("Failed to create updates directory.");
        return false;
    }
    if (!dir.mkpath(QFileInfo(maxHookRuntimePath()).absolutePath())) {
        if (error) *error = QStringLiteral("Failed to create MaxHook runtime directory.");
        return false;
    }
    return true;
}

bool ProfileStore::stagePackagedMaxHook(QString* error) {
    const QString sourcePath = QDir(QCoreApplication::applicationDirPath())
            .filePath(QStringLiteral("maxhook/MaxHook.dll"));
    QFile source(sourcePath);
    if (!source.exists()) {
        if (error) *error = QStringLiteral("Packaged MaxHook is absent; official-install fallback remains available.");
        return false;
    }
    if (!ensureDirectories(error)) return false;
    if (!source.open(QIODevice::ReadOnly)) {
        if (error) *error = QStringLiteral("Failed to read packaged MaxHook.");
        return false;
    }

    QSaveFile target(maxHookRuntimePath());
    if (!target.open(QIODevice::WriteOnly)) {
        if (error) *error = QStringLiteral("Failed to open the staged MaxHook target.");
        return false;
    }
    while (!source.atEnd()) {
        const QByteArray chunk = source.read(1024 * 1024);
        if (chunk.isEmpty() && source.error() != QFileDevice::NoError) {
            target.cancelWriting();
            if (error) *error = QStringLiteral("Failed while reading packaged MaxHook.");
            return false;
        }
        if (target.write(chunk) != chunk.size()) {
            target.cancelWriting();
            if (error) *error = QStringLiteral("Failed while staging packaged MaxHook.");
            return false;
        }
    }
    if (!target.commit()) {
        if (error) *error = QStringLiteral("Failed to atomically commit staged MaxHook.");
        return false;
    }
    return true;
}

} // namespace loader
