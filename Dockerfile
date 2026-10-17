FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY target/scala-communicator-server-*.jar /app/server.jar
COPY target/scala-communicator-client-*.jar /app/client.jar

ENV ROLE=server
ENV SEED_HOST=server
ENV SEED_PORT=2551
ENV MY_PORT=2552

EXPOSE 2551 2552 9000/udp 9001 9002

# Explicit main class selection — the fat jar's default main is ServerApp,
# so clients must override it.
CMD ["/bin/bash", "-c", "if [ \"$ROLE\" = \"client\" ]; then exec java -cp /app/client.jar com.example.comm.client.ClientApp \"$SEED_HOST\" \"$SEED_PORT\" \"$MY_PORT\"; else exec java -jar /app/server.jar; fi"]
