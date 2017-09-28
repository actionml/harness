FROM harness/vw:8.2.1

WORKDIR /app

ADD . /tmp
ADD ./entrypoint.sh /app

RUN cd /tmp && \
    ./make-distribution.sh && \
    tar zxvf ./Harness-0.1.0-SNAPSHOT.tar.gz && \
    cp -a ./Harness-0.1.0-SNAPSHOT/. /app/ && \
    cd /app && \
    rm -R /tmp/*

ENTRYPOINT ["/app/entrypoint.sh"]
