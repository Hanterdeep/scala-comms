FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
# Jars may land in target/ or target/scala-2.13/ depending on sbt settings
COPY target/scala-communicator-server-*.jar /app/server.jar
COPY target/scala-communicator-client-*.jar /app/client.jar

ENV ROLE=server
ENV SEED_HOST=server
ENV SEED_PORT=2551
ENV MY_PORT=2552

EXPOSE 2551 2552 9000/udp 9001 9002

CMD ["/bin/bash", "-c", "if [ \"$ROLE\" = \"client\" ]; then java -jar /app/client.jar $SEED_HOST $SEED_PORT $MY_PORT; else java -jar /app/server.jar; fi"]
