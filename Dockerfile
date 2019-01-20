FROM maven:3.6.0-jdk-8-alpine

WORKDIR /usr/src/bot
COPY . /usr/src/bot

RUN mvn clean package

WORKDIR /usr/src/app
CMD ["java", "-jar", "/usr/src/bot/target/googram-1.0-SNAPSHOT.jar"]
