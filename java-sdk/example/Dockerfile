FROM actionml/java:8

ADD . /app

WORKDIR /app

RUN mvn compile

ENTRYPOINT ["/app/entrypoint.sh"]