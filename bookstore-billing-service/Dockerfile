FROM eclipse-temurin:17-jre-alpine

VOLUME /tmp

ARG JAR_FILE

COPY target/${JAR_FILE} billing-service.jar

COPY dockerize dockerize

CMD ./dockerize -wait tcp://${DB_HOST:bookstore-mysql-db}:${DB_PORT:3306} -timeout 15m \
    java -Djava.security.egd=file:/dev/./urandom \
    -jar /billing-service.jar
