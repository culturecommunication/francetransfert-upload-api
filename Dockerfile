FROM openjdk:11.0.4-jre-stretch
VOLUME /tmp
ADD target/FTR-upload-api-0.2.0.jar francetransfert-upload-api.jar
EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/francetransfert-upload-api.jar"]
