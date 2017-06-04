FROM actionml/java:8

ENV VW_VERSION=8.2.1

RUN apt-get update \
    && apt-get -y install build-essential automake autoconf libxmu-dev gcc libpthread-stubs0-dev libtool libboost-program-options-dev zlib1g-dev libc6 libgcc1 libstdc++6 libboost-python-dev wget unzip \
    && apt-get autoremove -y --force-yes \
    && export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/local/lib \
    && wget https://github.com/JohnLangford/vowpal_wabbit/archive/${VW_VERSION}.zip \
    && unzip ./${VW_VERSION}.zip \
    && cd vowpal_wabbit-${VW_VERSION} \
    && ./autogen.sh \
    && ./configure --with-boost-libdir=/usr/lib/x86_64-linux-gnu \
    && make \
    && make install \
    && cd ../ \
    && rm -R vowpal_wabbit-${VW_VERSION}