FROM openjdk:8-jre-alpine3.8

ARG version
LABEL com.actionml.harness.vendor=ActionML \
      com.actionml.harness.service=rest-api \
      com.actionml.harness.version=$version

ENV LANG=C.UTF-8 \
    CONFD_VERSION=v0.16.0 \
    CONFD_SHA256=255d2559f3824dd64df059bdc533fd6b697c070db603c76aaf8d1d5e6b0cc334 \
    PATH=$PATH:/harness/bin/

COPY ./dist /harness

RUN cd /tmp && \
	apk add --no-cache --update tini curl bash procps && \
	apk add --no-cache --update python3 && \
	apk add --virtual build-dependencies build-base gcc zlib-dev openssl-dev libffi-dev python3-dev && \
	apk add boost-dev git perl clang maven && \
	python3 -m ensurepip && \
	rm -r /usr/lib/python*/ensurepip && \
	pip3 install --upgrade pip setuptools && \
	ln -sf /usr/bin/python3 /usr/bin/python && \
	ln -sf /usr/bin/python3-config /usr/bin/python-config && \
	ln -sf /usr/bin/pydoc3 /usr/bin/pydoc && \
	ln -sf /usr/bin/pip3 /usr/bin/pip && \
	rm -r /root/.cache && \
	pip install /harness/wheel/*.whl && \
	curl -#SL -o /usr/local/bin/confd https://github.com/kelseyhightower/confd/releases/download/${CONFD_VERSION}/confd-${CONFD_VERSION#v}-linux-amd64 && \
	echo "${CONFD_SHA256}  /usr/local/bin/confd" | sha256sum -c && chmod 755 /usr/local/bin/confd

RUN cd /tmp && \
	git clone https://github.com/pferrel/vowpal_wabbit.git vw && \
	cd vw && \
	git checkout 10bd09ab06f59291e04ad7805e88fd3e693b7159 && \
	make java && cd java && \
	cp target/libvw_jni.so /usr/lib/jni && \
	rm -rf /tmp && rm -fr /var/cache/apk/*

WORKDIR /harness
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["./bin/harness","start"]
