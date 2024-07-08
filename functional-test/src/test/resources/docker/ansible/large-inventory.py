#!/usr/bin/env python3
import json

nodes_meta = {}
nodes = []

for x in range(8000):
    node_name = "Node-" + str(x)
    nodes_meta[node_name] = {
        "ansible_ssh_user": "rundeck",
        "ansible_host": "ssh-node",
        "some-var": "1234"
    }

    nodes.append(node_name)


output = {
    "_meta": {
        "hostvars": nodes_meta
    },
    "test": {
        "hosts": nodes
    }
}



print(json.dumps(output))
