FROM eclipse-temurin:17-jre-alpine

VOLUME /tmp

ARG JAR_FILE

COPY target/${JAR_FILE} payment-service.jar

COPY dockerize dockerize

CMD ./dockerize -wait tcp://${DB_HOST:bookstore-mysql-db}:${DB_PORT:3306} -timeout 15m \
    java -Djava.security.egd=file:/dev/./urandom \
    -jar /payment-service.jar
