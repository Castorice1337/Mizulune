package shit.zen.build

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

import java.security.SecureRandom
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class MizuluneObfuscationPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.extraProperties.set('obfuscateJar', { File jarFile, File mappingOut ->
            obfuscateJar(project, jarFile, mappingOut)
        })

        project.tasks.register('obfuscateClasses') { task ->
            task.group = 'openzen'
            task.description = 'Rename every OpenZen class to an opaque name in the built jar (class names only).'
            task.dependsOn 'reobfJar'
            task.doLast {
                def jarTask = project.tasks.named('jar', Jar).get()
                obfuscateJar(project, jarTask.archiveFile.get().asFile, project.file("${project.buildDir}/rename-mapping.txt"))
            }
        }

        project.tasks.matching { it.name == 'reobfJar' }.configureEach { task ->
            task.finalizedBy 'obfuscateClasses'
        }
    }

    private static void obfuscateJar(Project project, File jarFile, File mappingOut) {
        def owned = { String internal -> internal.startsWith('shit/zen/') || internal.startsWith('asm/patchify/') }

        def ownedNames = []
        new ZipFile(jarFile).withCloseable { zf ->
            for (entry in Collections.list(zf.entries())) {
                if (!entry.directory && entry.name.endsWith('.class')) {
                    def internal = entry.name.substring(0, entry.name.length() - 6)
                    if (owned(internal)) {
                        ownedNames << internal
                    }
                }
            }
        }
        if (ownedNames.isEmpty()) {
            project.logger.lifecycle("obfuscateJar: no original class names in ${jarFile.name} (already obfuscated) - skipping")
            return
        }

        def typeMap = [:]
        def usedNames = new HashSet()
        def secureRandom = new SecureRandom()
        def leadAlphabet = (('a'..'z') + ('A'..'Z')).join('')
        def nameAlphabet = (('a'..'z') + ('A'..'Z') + ('0'..'9')).join('')
        def randomName = {
            def sb = new StringBuilder(16)
            sb.append(leadAlphabet.charAt(secureRandom.nextInt(leadAlphabet.length())))
            15.times { sb.append(nameAlphabet.charAt(secureRandom.nextInt(nameAlphabet.length()))) }
            sb.toString()
        }
        def obfPackage = randomName()
        ownedNames.each { internal ->
            def newName
            while (true) {
                newName = randomName()
                if (usedNames.add(newName)) {
                    break
                }
            }
            typeMap[internal] = obfPackage + '/' + newName
        }

        def stringMap = [:]
        typeMap.each { originalName, mappedName ->
            stringMap[originalName.replace('/', '.')] = mappedName.replace('/', '.')
            stringMap[originalName] = mappedName
        }

        def remapper = new Remapper() {
            @Override
            String map(String internalName) {
                def mappedName = typeMap[internalName]
                return mappedName != null ? mappedName : internalName
            }

            @Override
            Object mapValue(Object value) {
                if (value instanceof String) {
                    def replacement = stringMap[value]
                    if (replacement != null) {
                        return replacement
                    }
                }
                return super.mapValue(value)
            }
        }

        def tmp = new File(jarFile.parentFile, jarFile.name + '.obf')
        tmp.delete()
        new ZipFile(jarFile).withCloseable { zf ->
            new ZipOutputStream(new FileOutputStream(tmp)).withCloseable { zos ->
                for (entry in Collections.list(zf.entries())) {
                    if (entry.directory) {
                        continue
                    }
                    def name = entry.name
                    byte[] bytes = zf.getInputStream(entry).bytes
                    if (name.endsWith('.class')) {
                        def internal = name.substring(0, name.length() - 6)
                        if (owned(internal)) {
                            def cr = new ClassReader(bytes)
                            def cw = new ClassWriter(0)
                            def stripSource = new ClassVisitor(Opcodes.ASM9, cw) {
                                @Override
                                void visitSource(String source, String debug) {
                                    super.visitSource(null, null)
                                }
                            }
                            cr.accept(new ClassRemapper(stripSource, remapper), 0)
                            bytes = cw.toByteArray()
                            name = typeMap[internal] + '.class'
                        }
                        zos.putNextEntry(new ZipEntry(name))
                        zos.write(bytes)
                        zos.closeEntry()
                    } else if (name == 'META-INF/MANIFEST.MF') {
                        def mf = new Manifest(new ByteArrayInputStream(bytes))
                        def attrs = mf.getMainAttributes()
                        ['Premain-Class', 'Agent-Class'].each { key ->
                            def value = attrs.getValue(key)
                            if (value != null) {
                                def internal = value.replace('.', '/')
                                if (typeMap.containsKey(internal)) {
                                    attrs.putValue(key, typeMap[internal].replace('/', '.'))
                                }
                            }
                        }
                        def bos = new ByteArrayOutputStream()
                        mf.write(bos)
                        zos.putNextEntry(new ZipEntry(name))
                        zos.write(bos.toByteArray())
                        zos.closeEntry()
                    } else if (name.startsWith('META-INF/') &&
                            (name.endsWith('.SF') || name.endsWith('.RSA') || name.endsWith('.DSA') || name.endsWith('.EC'))) {
                        // Renaming class entries invalidates signature digests.
                    } else {
                        zos.putNextEntry(new ZipEntry(name))
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
            }
        }
        if (!jarFile.delete()) {
            throw new GradleException("obfuscateJar: could not delete ${jarFile}")
        }
        if (!tmp.renameTo(jarFile)) {
            throw new GradleException("obfuscateJar: could not move ${tmp} -> ${jarFile}")
        }

        mappingOut.parentFile.mkdirs()
        mappingOut.withWriter('UTF-8') { writer ->
            typeMap.sort { it.key }.each { originalName, mappedName ->
                writer.writeLine("${originalName.replace('/', '.')} -> ${mappedName.replace('/', '.')}")
            }
        }

        def bridgeInternalName = 'shit/zen/dll/GameLoaderBridge'
        def bridgeName = typeMap[bridgeInternalName]
        if (bridgeName == null) {
            throw new GradleException("obfuscateJar: missing ${bridgeInternalName} in ${jarFile}")
        }
        def bridgeFqcn = bridgeName.replace('/', '.')
        def header = project.file('native/dll/src/generated_names.h')
        header.parentFile.mkdirs()
        header.text = """\
// AUTO-GENERATED by MizuluneObfuscationPlugin. DO NOT EDIT, DO NOT COMMIT.
// The build renames every OpenZen class to an opaque name; this captures the
// generated FQCN of the DLL bootstrap bridge (originally shit.zen.dll.GameLoaderBridge)
// so the native loader can request it by name. The bridge's load(String, ClassLoader)
// method name is preserved by the rename, so main.cpp's GetStaticMethodID still works.
#pragma once
#define OZ_BRIDGE_FQCN "${bridgeFqcn}"
"""

        project.logger.lifecycle("obfuscateJar: renamed ${typeMap.size()} classes in ${jarFile.name}; " +
                "bridge=${bridgeFqcn}; mapping -> ${mappingOut}")
    }
}
