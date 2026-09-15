package com.rundeck.plugins.ansible.util

import com.dtolabs.rundeck.core.common.Framework
import com.dtolabs.rundeck.core.utils.PropertyLookup
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files

class AnsibleUtilSpec extends Specification{

    @TempDir
    File tempDir

    def "createTemporaryFile should create a temporary file with the correct properties"() {
        given: "Input parameters for the temporary file"
        String prefix = inputPrefix
        String suffix = ".txt"
        String data = "test data"
        String path = tempDir.absolutePath

        when: "The method is called"
        File result = AnsibleUtil.createTemporaryFile(prefix, suffix, data, path)

        then: "The file is created in the specified path with the correct content"
        result.exists()
        result.parent == tempDir.absolutePath
        result.name.startsWith(expectedPrefix)
        result.name.endsWith(suffix)
        new String(Files.readAllBytes(result.toPath())) == data

        where:
        inputPrefix        || expectedPrefix
        "my-prefix"        || "my-prefix"
        ""                 || "ansible-runner"
    }

    def "getCustomTmpPathDir should return the correct tmp path based on framework properties"() {
        given: "A mock Framework and PropertyLookup"
        def framework = Mock(Framework)
        def propertyLookup = Mock(PropertyLookup)
        framework.getPropertyLookup() >> propertyLookup

        and: "A specific value for framework.tmp.dir property"
        propertyLookup.getProperty("framework.tmp.dir") >> frameworkTmpDir

        and: "A system property for java.io.tmpdir"
        System.setProperty("java.io.tmpdir", systemTmpDir)

        when: "The method is called"
        def result = AnsibleUtil.getCustomTmpPathDir(framework)

        then: "The expected tmp path is returned"
        result == expectedTmpDir

        where:
        frameworkTmpDir       | systemTmpDir       || expectedTmpDir
        "custom/tmp/dir"      | "/default/tmp"     || "custom/tmp/dir"   // Case where framework.tmp.dir is set
        ""                    | "/default/tmp"     || "/default/tmp"     // Case where framework.tmp.dir is empty
        null                  | "/default/tmp"     || "/default/tmp"     // Case where framework.tmp.dir is null
    }

    // RUN-4821: shared normalization used by both the gather-facts JSON path
    // (AnsibleResourceModelSource) and the static-inventory path (InventoryList) so
    // osFamily is always Rundeck's canonical "unix"/"windows", never a raw
    // ansible_system/ansible_os_family value like "Linux" or "Debian".
    def "normalizeOsFamily normalizes #rawOsFamily to #expected"() {
        expect:
        AnsibleUtil.normalizeOsFamily(rawOsFamily) == expected

        where:
        rawOsFamily | expected
        'Linux'     | 'unix'
        'Windows'   | 'windows'
        'windows'   | 'windows'
        'Darwin'    | 'unix'
        'Debian'    | 'unix'
        'RedHat'    | 'unix'
        'FreeBSD'   | 'unix'
        'Win32NT'   | 'windows'
        'Win32S'    | 'windows'
        'Win32Windows' | 'windows'
        'WinCE'     | 'windows'
        'WIN32NT'   | 'windows'
        ' Windows ' | 'windows'
        null        | null
    }
}

