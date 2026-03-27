FROM eclipse-temurin:17-jre

WORKDIR /app

COPY build/libs/srim-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-Djdk.tls.client.protocols=TLSv1.2", "-jar", "app.jar"]