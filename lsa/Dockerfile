FROM openjdk:8

RUN apt-get update

WORKDIR /root

RUN wget http://d3kbcqa49mib13.cloudfront.net/spark-2.1.0-bin-hadoop2.7.tgz && \
    tar -xvzf spark-2.1.0-bin-hadoop2.7.tgz &&
    rm -f spark-2.1.0-bin-hadoop2.7.tgz

VOLUME /root/apps

CMD ["/bin/bash"]