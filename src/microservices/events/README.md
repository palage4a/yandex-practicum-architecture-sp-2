# Events

Events service for Cinema Abyss.

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen


## Local running

To start a web server for the application, run:

    lein uberjar
    java -jar target/uberjar/events-0.1.0-SNAPSHOT-standalone.jar


## Docker

To build Docker image with the application, run:

    docker build -t cinemaabyss/events:0.1.0 .

To start a Docker container, run:

    docker run --rm -it -p 8082:8082  cinemaabyss/events:0.1.0
