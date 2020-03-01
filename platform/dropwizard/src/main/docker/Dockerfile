# On OpenJDK 8 Slim Debian Image

# FROM gregjan/ubuntu-openjdk8
FROM openjdk:8-jre-alpine
LABEL maintainer="Greg Jansen <jansen@umd.edu>"

ADD build/distributions/trellis-db-*.tar /opt
RUN mv /opt/trellis-db-* /opt/trellis
COPY src/main/docker/command.sh /
RUN chmod +x /command.sh

CMD ["/command.sh"]
