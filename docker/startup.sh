#!/bin/bash
cp  /etc/settings/ansible/id_rsa /home/rundeck/.ssh/id_rsa
cp  /etc/settings/ansible/id_rsa /var/lib/rundeck/.ssh/id_rsa
cp  /etc/settings/ansible/hosts /etc/ansible
cp  /etc/settings/ansible/ansible.cfg /etc/ansible
chown -R rundeck:rundeck /var/lib/rundeck
chmod 600 /var/lib/rundeck/.ssh/id_rsa

echo "StrictHostKeyChecking no" > /var/lib/rundeck/.ssh/config
echo "UserKnownHostsFile /dev/null" >> /var/lib/rundeck/.ssh/config

service rundeckd restart
tail -F /var/log/rundeck/service.log
