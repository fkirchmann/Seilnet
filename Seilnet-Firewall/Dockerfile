# Stage that builds the JAR, a prerequisite for the running stage
FROM maven:3-eclipse-temurin-17-alpine as build
WORKDIR /build

# Copy pom.xml and prefetch dependencies so a repeated build can continue from the next step with existing dependencies
COPY pom.xml ./
COPY Seilnet-Shared/pom.xml ./Seilnet-Shared/pom.xml
COPY Seilnet-Main/pom.xml ./Seilnet-Main/pom.xml
COPY Seilnet-Firewall/pom.xml ./Seilnet-Firewall/pom.xml
RUN mvn dependency:go-offline

COPY Seilnet-Shared ./Seilnet-Shared
COPY Seilnet-Firewall ./Seilnet-Firewall
RUN mvn package -pl Seilnet-Firewall -am -DskipTests

# Second stage: Run the JAR
FROM eclipse-temurin:17-jdk-alpine

COPY --from=build /build/Seilnet-Firewall/target/Seilnet-*.jar /app/app.jar

WORKDIR /config
VOLUME /config
# Required for Inet4Address (de)serialization in XStream, see https://github.com/x-stream/xstream/issues/262
CMD ["java", "-jar", "/app/app.jar"]

# run this from the root directory of the project
# Usage: docker build -t seilnet-firewall -f Seilnet-Firewall/Dockerfile .
# To run: docker run -v /path/to/config:/config seilnet-firewall