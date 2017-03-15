ROM ubuntu:14.04
MAINTAINER Kaushik Chakraborty <git@kaushikc.org>

LABEL "Dockerfile to install DeepDive"

RUN apt-get update && \
    apt-get install -y curl

WORKDIR /root

RUN curl -fsSLq git.io/getdeepdive > getdd.sh && \
    chmod 744 getdd.sh && \
    /bin/bash getdd.sh _deepdive_runtime_deps deepdive_from_release

ENV PATH=/root/local/bin:$PATH

WORKDIR /root/apps

VOLUME /root/apps

CMD ["/bin/bash"]
