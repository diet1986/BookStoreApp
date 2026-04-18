FROM eclipse-temurin:17-jre-alpine

VOLUME /tmp

ARG JAR_FILE

COPY target/${JAR_FILE} account-service.jar

# dockerize waits for MySQL to be ready before starting the service
COPY dockerize dockerize

CMD ./dockerize -wait tcp://${DB_HOST:bookstore-mysql-db}:${DB_PORT:3306} -timeout 15m \
    java -Djava.security.egd=file:/dev/./urandom \
    -jar /account-service.jar
