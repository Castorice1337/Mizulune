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
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

import java.security.SecureRandom
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class MizuluneObfuscationPlugin implements Plugin<Project> {
    private static final String SYNC_TOKEN_INTERNAL_NAME = 'com/heypixel/heypixelmod/SyncToken'
    private static final String SYNC_TOKEN_DOTTED_NAME = 'com.heypixel.heypixelmod.SyncToken'
    private static final String SYNC_TOKEN_ENTRY = SYNC_TOKEN_INTERNAL_NAME + '.class'
    private static final String ACCEPT_DESCRIPTOR = '(Ljava/lang/String;)V'
    private static final String LOGOUT_DESCRIPTOR = '()V'

    @Override
    void apply(Project project) {
        project.extensions.extraProperties.set('obfuscateJar', { File jarFile, File mappingOut ->
            obfuscateJar(project, jarFile, mappingOut)
        })

        def obfuscateClasses = project.tasks.register('obfuscateClasses') { task ->
            task.group = 'openzen'
            task.description = 'Rename every OpenZen class to an opaque name in the built jar (class names only).'
            task.dependsOn 'reobfJar'
            task.doLast {
                def jarTask = project.tasks.named('jar', Jar).get()
                obfuscateJar(project, jarTask.archiveFile.get().asFile, project.file("${project.buildDir}/rename-mapping.txt"))
            }
        }

        def verifySyncTokenAbiTask = project.tasks.register('verifySyncTokenAbi') { task ->
            task.group = 'verification'
            task.description = 'Verify the final obfuscated jar preserves the exact SyncToken ABI.'
            task.dependsOn obfuscateClasses
            task.doLast {
                def jarTask = project.tasks.named('jar', Jar).get()
                verifySyncTokenAbi(
                        project,
                        jarTask.archiveFile.get().asFile,
                        project.file("${project.buildDir}/rename-mapping.txt"))
            }
        }

        project.tasks.matching { it.name == 'check' }.configureEach { task ->
            task.dependsOn verifySyncTokenAbiTask
        }

        project.tasks.matching { it.name == 'stageNativeJar' }.configureEach { task ->
            task.dependsOn verifySyncTokenAbiTask
        }

        project.tasks.matching { it.name == 'reobfJar' }.configureEach { task ->
            task.finalizedBy 'obfuscateClasses'
        }
    }

    private static void obfuscateJar(Project project, File jarFile, File mappingOut) {
        def owned = { String internal ->
            !isExactAbiKeep(internal)
                    && (internal.startsWith('shit/zen/') || internal.startsWith('asm/patchify/'))
        }

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

    private static boolean isExactAbiKeep(String internalName) {
        return SYNC_TOKEN_INTERNAL_NAME == internalName
    }

    private static void verifySyncTokenAbi(Project project, File jarFile, File mappingOut) {
        if (!jarFile.isFile()) {
            throw new GradleException("verifySyncTokenAbi: missing final jar ${jarFile}")
        }
        if (!mappingOut.isFile()) {
            throw new GradleException("verifySyncTokenAbi: missing rename mapping ${mappingOut}")
        }

        int acceptCallsiteCount = 0
        int logoutCallsiteCount = 0
        new ZipFile(jarFile).withCloseable { zf ->
            def entries = Collections.list(zf.entries()).findAll { !it.directory }
            def exactEntries = entries.findAll { it.name == SYNC_TOKEN_ENTRY }
            requireAbi(exactEntries.size() == 1,
                    "expected exactly one ${SYNC_TOKEN_ENTRY} entry, found ${exactEntries.size()}")

            byte[] exactBytes = zf.getInputStream(exactEntries[0]).bytes
            ClassNode exactClass = new ClassNode()
            new ClassReader(exactBytes).accept(exactClass, 0)
            verifyExactClass(exactClass)

            entries.findAll { it.name.endsWith('.class') }.each { entry ->
                ClassNode caller = new ClassNode()
                new ClassReader(zf.getInputStream(entry).bytes).accept(
                        caller, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
                caller.methods.each { MethodNode method ->
                    method.instructions.toArray().findAll { it instanceof MethodInsnNode }.each {
                        MethodInsnNode invocation = (MethodInsnNode) it
                        if (SYNC_TOKEN_INTERNAL_NAME == invocation.owner) {
                            boolean validAccept = invocation.name == 'accept'
                                    && invocation.desc == ACCEPT_DESCRIPTOR
                            boolean validLogout = invocation.name == 'logout'
                                    && invocation.desc == LOGOUT_DESCRIPTOR
                            requireAbi(invocation.opcode == Opcodes.INVOKESTATIC
                                            && !invocation.itf
                                            && (validAccept || validLogout),
                                    "invalid SyncToken callsite in ${caller.name}.${method.name}${method.desc}: "
                                            + "opcode=${invocation.opcode} ${invocation.owner}."
                                            + "${invocation.name}${invocation.desc}")
                            if (validAccept) {
                                acceptCallsiteCount++
                            } else {
                                logoutCallsiteCount++
                            }
                        }
                    }
                }
            }
        }

        String mappingText = mappingOut.getText('UTF-8')
        requireAbi(!mappingText.contains(SYNC_TOKEN_INTERNAL_NAME)
                        && !mappingText.contains(SYNC_TOKEN_DOTTED_NAME),
                "rename mapping must not contain the exact SyncToken owner")
        requireAbi(acceptCallsiteCount == 1,
                "expected exactly one SyncToken.accept callsite, found ${acceptCallsiteCount}")
        requireAbi(logoutCallsiteCount == 1,
                "expected exactly one SyncToken.logout callsite, found ${logoutCallsiteCount}")

        project.logger.lifecycle("verifySyncTokenAbi: PASS jar=${jarFile.name} "
                + "acceptCallsites=${acceptCallsiteCount} logoutCallsites=${logoutCallsiteCount}")
    }

    private static void verifyExactClass(ClassNode node) {
        requireAbi(node.version == Opcodes.V17,
                "SyncToken major version must be 61, found ${node.version}")
        requireAbi(node.access == (Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER),
                "SyncToken class flags must be ACC_PUBLIC|ACC_SUPER, found 0x"
                        + Integer.toHexString(node.access))
        requireAbi(node.name == SYNC_TOKEN_INTERNAL_NAME,
                "SyncToken owner mismatch: ${node.name}")
        requireAbi(node.superName == 'java/lang/Object',
                "SyncToken superclass mismatch: ${node.superName}")
        requireAbi(node.interfaces.isEmpty(), "SyncToken must not implement interfaces")
        requireAbi(node.fields.isEmpty(), "SyncToken must have zero fields")
        requireAbi(node.methods.size() == 3,
                "SyncToken must have exactly three methods, found ${node.methods.size()}")

        requireMethod(node, '<init>', '()V', Opcodes.ACC_PUBLIC)
        MethodNode accept = requireMethod(
                node, 'accept', ACCEPT_DESCRIPTOR, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
        MethodNode logout = requireMethod(
                node, 'logout', LOGOUT_DESCRIPTOR, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
        requireEmptyReturn(accept)
        requireEmptyReturn(logout)
    }

    private static MethodNode requireMethod(
            ClassNode owner, String name, String descriptor, int access) {
        def matches = owner.methods.findAll { it.name == name && it.desc == descriptor }
        requireAbi(matches.size() == 1,
                "expected exactly one ${name}${descriptor}, found ${matches.size()}")
        MethodNode method = (MethodNode) matches[0]
        requireAbi(method.access == access,
                "${name}${descriptor} flags mismatch: 0x${Integer.toHexString(method.access)}")
        requireAbi(method.signature == null, "${name}${descriptor} must not have a generic signature")
        requireAbi(method.exceptions == null || method.exceptions.isEmpty(),
                "${name}${descriptor} must not declare exceptions")
        return method
    }

    private static void requireEmptyReturn(MethodNode method) {
        def opcodes = method.instructions.toArray()
                .findAll { it.opcode >= 0 }
                .collect { it.opcode }
        requireAbi(opcodes == [Opcodes.RETURN],
                "${method.name}${method.desc} must contain only RETURN, found ${opcodes}")
        requireAbi(method.tryCatchBlocks.isEmpty(),
                "${method.name}${method.desc} must not contain try/catch blocks")
    }

    private static void requireAbi(boolean condition, String message) {
        if (!condition) {
            throw new GradleException("verifySyncTokenAbi: ${message}")
        }
    }
}
