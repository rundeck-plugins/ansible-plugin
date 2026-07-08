package functional.util

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.rundeck.client.api.model.ExecLog

import groovy.io.FileType

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.text.SimpleDateFormat
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.stream.Collectors

class TestUtil {

    static File createArchiveJarFile(String name, File projectArchiveDirectory) {
        if(!projectArchiveDirectory.isDirectory()){
            throw new IllegalArgumentException("Must be a directory")
        }
        //create a project archive from the contents of the directory
        def tempFile = File.createTempFile("import-temp-${name}", ".zip")
        tempFile.deleteOnExit()
        //create Manifest
        def manifest = new Manifest()
        manifest.mainAttributes.putValue("Manifest-Version", "1.0")
        manifest.mainAttributes.putValue("Rundeck-Archive-Project-Name", name)
        manifest.mainAttributes.putValue("Rundeck-Archive-Format-Version", "1.0")
        // Must align with the Rundeck server under test; Rundeck 6 can reject archives stamped as 5.x (functionalTest sets RUNDECK_ARCHIVE_APP_VERSION).
        manifest.mainAttributes.putValue(
                "Rundeck-Application-Version",
                System.getProperty("RUNDECK_ARCHIVE_APP_VERSION", "6.0.0"))
        manifest.mainAttributes.putValue(
                "Rundeck-Archive-Export-Date",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").format(new Date())
        )

        Path base = projectArchiveDirectory.toPath()
        List<File> filesOnly = []
        projectArchiveDirectory.eachFileRecurse(FileType.FILES) { f -> filesOnly.add(f) }

        // Rundeck 6 async import expects directory entries with trailing /. Otherwise "jobs" can extract as a
        // zero-byte file and AsyncImportService fails with "Not a directory".
        Set<String> directoryEntries = new LinkedHashSet<>()
        for (File f : filesOnly) {
            String rel = base.relativize(f.toPath()).toString().replace('\\', '/')
            String[] parts = rel.split('/')
            for (int i = 0; i < parts.length - 1; i++) {
                directoryEntries.add((parts[0..i].join('/') + '/') as String)
            }
        }
        List<String> dirsOrdered = new ArrayList<>(directoryEntries)
        dirsOrdered.sort { a, b ->
            int da = a.count('/')
            int db = b.count('/')
            da != db ? da <=> db : a <=> b
        }

        tempFile.withOutputStream { os ->
            JarOutputStream jos = new JarOutputStream(os, manifest)
            jos.withCloseable { JarOutputStream jarOutputStream ->
                for (String dir : dirsOrdered) {
                    jarOutputStream.putNextEntry(new JarEntry(dir))
                    jarOutputStream.closeEntry()
                }
                for (File file : filesOnly) {
                    String entryName = base.relativize(file.toPath()).toString().replace('\\', '/')
                    jarOutputStream.putNextEntry(new JarEntry(entryName))
                    file.withInputStream { is -> jarOutputStream << is }
                    jarOutputStream.closeEntry()
                }
            }
        }
        tempFile
    }

    static Map<String, Integer> getAnsibleNodeResult(List<ExecLog> logs){
        String nodeResumeLog = null

        for(ExecLog execLog: logs){
            if(execLog.log.startsWith("ssh-node") && execLog.log.contains("ok=")){
                nodeResumeLog = execLog.log
            }
        }

        if(nodeResumeLog==null){
            return null
        }

        List<String> listNodeResultStatus = nodeResumeLog.split("\\s",-1).findAll{!it.isEmpty() && it.contains("=")}

        return listNodeResultStatus.stream().collect(Collectors.toMap(s -> s.toString().split("=")[0], s -> Integer.valueOf(s.toString().split("=")[1])))
    }

    static def generatePrivateKey(String filePath, String keyName, String passphrase = null){
        JSch jsch=new JSch()
        KeyPair keyPair=KeyPair.genKeyPair(jsch, KeyPair.RSA)
        if(passphrase){
            keyPair.writePrivateKey(filePath + File.separator + keyName, passphrase.getBytes())
        }else{
            keyPair.writePrivateKey(filePath + File.separator + keyName)
        }

        keyPair.writePublicKey(filePath + File.separator + keyName + ".pub", "test private key")

        keyPair.dispose()

        File privateKey = new File(filePath + File.separator + keyName)
        Set<PosixFilePermission> perms = new HashSet<PosixFilePermission>()
        perms.add(PosixFilePermission.OWNER_READ)
        perms.add(PosixFilePermission.OWNER_WRITE)
        Files.setPosixFilePermissions(privateKey.toPath(), perms)
    }

}
