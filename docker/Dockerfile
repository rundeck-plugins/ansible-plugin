# base installation of rundeck
# run as: docker run -p 4440:4440 -v #ANSIBLE_CONFIG_FOLDER:/etc/settings/ansible   giovannipaologibilisco/rundeck
# ANSIBLE_CONFIG_FOLDER should contain at least to following:
#  - the file hosts with the groups of hosts to control. This file should contain also the static ip like:
#     server1 192.168.100.1
#  - the file with a private key, named id_rsa, to use for connection
FROM ubuntu:14.04

MAINTAINER Giovanni Paolo Gibilisco version: 0.1
VOLUME /etc/settings/ansible

RUN apt-get update && apt-get install -y\
    software-properties-common

RUN apt-add-repository ppa:ansible/ansible \
    && apt-get update && apt-get install -y\
    wget \
    openjdk-7-jdk \
    software-properties-common \
    ansible \
    && rm -rf /var/lib/apt/lists/*

RUN wget http://dl.bintray.com/rundeck/rundeck-deb/rundeck-2.6.6-1-GA.deb \
    && dpkg -i rundeck-2.6.6-1-GA.deb \
    && rm rundeck-2.6.6-1-GA.deb

RUN wget https://github.com/Batix/rundeck-ansible-plugin/releases/download/1.2.2/ansible-plugin-1.2.2.jar \
    && mv ansible-plugin-1.2.2.jar /var/lib/rundeck/libext

RUN mkdir -p /home/rundeck/.ssh
RUN mkdir /var/lib/rundeck/.ssh
RUN chsh -s /bin/bash rundeck

ADD ./startup.sh /home/rundeck/startup.sh

EXPOSE 4440

CMD /home/rundeck/startup.sh
