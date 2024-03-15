#!/bin/bash

rm /configuration/id_rsa
rm /configuration/id_rsa.pub
rm /configuration/authorized_keys
rm /configuration/known_hosts

ssh-keygen -q -t rsa -N '' -f /configuration/id_rsa

touch /configuration/known_hosts
touch /configuration/authorized_keys

chown agent:root /configuration/id_rsa
chown agent:root /configuration/id_rsa.pub
chown agent:root /configuration/known_hosts
chown agent:root /configuration/authorized_keys