FROM ubuntu:14.04

RUN apt-get update && apt-get install -y apt-transport-https \
    && echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee -a /etc/apt/sources.list \
    && echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee -a /etc/apt/sources.list \
    && echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | /usr/bin/debconf-set-selections \
    && apt-key adv --keyserver keyserver.ubuntu.com --recv-keys EEA14886 \
    && echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list \
    && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 \
    && apt-get update \
    && apt-get install -y curl dnsutils oracle-java8-installer ca-certificates \
    && apt-get install -y sbt \
    && apt-get install -y maven \
    && apt-get install -y python python-pip \
    && pip install --upgrade pip \
    && pip install --upgrade actionml