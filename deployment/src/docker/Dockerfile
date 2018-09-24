# On OpenJDK 8 Slim Debian Image

# FROM gregjan/ubuntu-openjdk8
FROM openjdk:8-jdk
MAINTAINER Greg Jansen <jansen@umd.edu>

ADD trellis-db-*.tar /opt
RUN mv /opt/trellis-db-* /opt/trellis
COPY command.sh /
COPY config.yml /
RUN chmod +x /command.sh

CMD ["/command.sh"]
