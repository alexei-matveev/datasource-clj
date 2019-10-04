#
# NOTE: Beware of the whitelisting in .dockerignore!
#
# Execute multistage build by
#
#     docker build -t f0bec0d/datasource-clj .
#
# and push it to Docker Hub:
#
#     docker login
#     docker push f0bec0d/datasource-clj
#
# To run a container issue
#
#     docker run --rm -itd -p 8080:8080 f0bec0d/datasource-clj
#
FROM clojure:lein AS builder
WORKDIR /work
ADD project.clj .
RUN find . && lein deps
ADD src src
# FWIW, uberjars, created  with lein uberjar, or all of  them (?)  are
# not "stable". Rebuild  with "lein uberjar" changes  the hash. Docker
# Layer Caching does  not let it come that far,  unless the sources or
# project file above change.
RUN find . && lein uberjar

FROM openjdk:8-jre-alpine
MAINTAINER alexei.matveev@gmail.com
WORKDIR /app

COPY --from=builder /work/target/datasource-clj-0.1.0-SNAPSHOT-standalone.jar /app/app.jar

EXPOSE 8080/tcp
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
