FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/top-ladder-api-0.0.1-SNAPSHOT-standalone.jar /top-ladder-api/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/top-ladder-api/app.jar"]
