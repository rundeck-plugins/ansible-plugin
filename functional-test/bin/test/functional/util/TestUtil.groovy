package functional.util

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.rundeck.client.api.model.ExecLog

import java.nio.file.Files
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
        manifest.mainAttributes.putValue("Rundeck-Application-Version", "5.0.0")
        manifest.mainAttributes.putValue(
                "Rundeck-Archive-Export-Date",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").format(new Date())
        )

        tempFile.withOutputStream { os ->
            def jos = new JarOutputStream(os, manifest)

            jos.withCloseable { jarOutputStream ->

                projectArchiveDirectory.eachFileRecurse { file ->
                    def entry = new JarEntry(projectArchiveDirectory.toPath().relativize(file.toPath()).toString())
                    jarOutputStream.putNextEntry(entry)
                    if (file.isFile()) {
                        file.withInputStream { is ->
                            jarOutputStream << is
                        }
                    }
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
