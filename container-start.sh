#!/bin/bash

CONFIG=${CONFIG:=application.conf}
LOGBACK_CONFIG=${LOGBACK_CONFIG:=logback.xml}

JAVA_OPTS="$JAVA_OPTS -Dconfig.resource=${CONFIG}"
JAVA_OPTS="$JAVA_OPTS -Dlogback.configurationFile=$LOGBACK_CONFIG"
JAVA_OPTS="$JAVA_OPTS -Dlog.label=$LOG_LABEL"

exec java $JAVA_OPTS -cp "./lib/*:./" ru.mobak.lm2.vknotificator.App
