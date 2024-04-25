package com.rundeck.plugins.ansible.plugin

import com.dtolabs.rundeck.core.common.INodeEntry
import com.dtolabs.rundeck.core.common.NodeEntryImpl
import com.dtolabs.rundeck.core.common.NodeSetImpl
import com.dtolabs.rundeck.core.resources.ResourceModelSource
import spock.lang.Specification

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class AnsibleResourceModelSourceSpec extends Specification {

    void "process node files"() {
        given:
        String nodeName = "localhost"
        NodeSetImpl nodes = new NodeSetImpl()
        INodeEntry entryNode = new NodeEntryImpl();
        entryNode.nodename = nodeName
        Stream<Path> directories = Stream.of(Path.of("/dir/file"))
        CompletableFuture<INodeEntry> future = CompletableFuture.completedFuture(entryNode)
        ResourceModelSource plugin = Spy(AnsibleResourceModelSource) {
            1 * processFile(*_) >> future
        }
        plugin.numberThreads = 1

        when:
        plugin.executeFutures(directories, nodes)

        then:
        future.get()

        then:
        nodes.size() == 1
        nodes[0].nodename == nodeName
    }
}


