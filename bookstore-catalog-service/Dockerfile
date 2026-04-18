FROM eclipse-temurin:17-jre-alpine

VOLUME /tmp

ARG JAR_FILE

COPY target/${JAR_FILE} catalog-service.jar

COPY dockerize dockerize

COPY ./images /images

ENV DB_HOST=bookstore-mysql-db
ENV DB_PORT=3306

CMD ./dockerize -wait tcp://${DB_HOST}:${DB_PORT} -timeout 15m \
    java -Djava.security.egd=file:/dev/./urandom \
    -jar /catalog-service.jar
