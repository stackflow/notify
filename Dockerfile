FROM registry.mobak.ru:5443/jre8:74

COPY target/lib /app/lib
COPY container-start.sh /app/
COPY target/jar /app/lib

WORKDIR /app

# THe exec is very important here, the command replaces start process to our one.
CMD exec ./container-start.sh
