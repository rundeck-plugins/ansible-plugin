# Ubuntu 16.04 based, runs as rundeck user
# https://hub.docker.com/r/rundeck/rundeck/tags
ARG RUNDECK_IMAGE
FROM ${RUNDECK_IMAGE}
MAINTAINER Rundeck Team

ENV ANSIBLE_HOST_KEY_CHECKING=false
ENV RDECK_BASE=/home/rundeck
ENV MANPATH=${MANPATH}:${RDECK_BASE}/docs/man
ENV PATH=${PATH}:${RDECK_BASE}/tools/bin
ENV PROJECT_BASE=${RDECK_BASE}/projects/Test-Project

USER root

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    curl vim unzip

# install ansible
RUN apt-get -y install sshpass && \
    apt-get -y install python3-pip && \
    apt-get -y install sudo && \
    pip3 install --upgrade pip

RUN pip3 install ansible

RUN ln -s /usr/bin/python3 /usr/bin/python

user rundeck

# add default project
COPY --chown=rundeck:rundeck project.properties ${PROJECT_BASE}/etc/

# remove embedded rundeck-ansible-plugin
#RUN zip -d rundeck.war WEB-INF/rundeck/plugins/ansible-plugin-* \
#  && unzip -C rundeck.war WEB-INF/rundeck/plugins/manifest.properties \
#  && sed -i "s/\(.*\)\(ansible-plugin-.*\.jar,\)\(.*\)/\1\3/" WEB-INF/rundeck/plugins/manifest.properties \
#  && zip -u rundeck.war WEB-INF/rundeck/plugins/manifest.properties \
#  && rm WEB-INF/rundeck/plugins/manifest.properties

# add locally built ansible plugin
COPY --chown=rundeck:rundeck plugins ${RDECK_BASE}/libext/
