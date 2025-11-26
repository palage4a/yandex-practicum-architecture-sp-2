FROM clojure:temurin-25-lein-alpine

WORKDIR /app

COPY project.clj /app/
RUN lein deps

COPY . /app

RUN lein uberjar

EXPOSE 8082

CMD ["java", "-jar", "target/uberjar/events-0.1.0-SNAPSHOT-standalone.jar"]