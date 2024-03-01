package com.rundeck.plugins.ansible.ansible

import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException
import spock.lang.Specification

import java.nio.file.Paths

class AnsibleRunnerSpec extends Specification {


    def "removing temp files"() {
        given:

        File tempFile = File.createTempDir("ansible-test", ".tmp")
        String extraVars = "key1=123"
        def runner = AnsibleRunner.playbookPath("gather-hosts.yml");
        runner.baseDirectory(tempFile.getAbsolutePath())
        runner.extraVars(extraVars)

        when:
        runner.run()
        then:
        Exception e = thrown(Exception)
        tempFile.listFiles().length == 0
    }
}
