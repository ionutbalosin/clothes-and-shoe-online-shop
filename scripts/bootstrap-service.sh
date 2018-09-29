#!/usr/bin/env bash

if [[ "$#" -lt 2 ]] ; then
    echo "Usage: ./bootstrap-service GROUP_ID VERSION [useJMC]"
    echo "  Examples:"
    echo "     ./bootstrap-service.sh redis-service-discovery 0.0.1-SNAPSHOT [true||false]"
    echo "     ./bootstrap-service.sh vertx-gateway 0.0.1-SNAPSHOT [true||false]"
    echo "     ./bootstrap-service.sh hat-service-provider 0.0.1-SNAPSHOT [true||false]"
    echo "     ./bootstrap-service.sh httpclient-shop 0.0.1-SNAPSHOT [true||false]"
    return
fi

GROUP_ID=$1
VERSION=$2

if [[ "$3" ==  "true" ]] ; then
     timestamp=`date "+%Y%m%d-%H%M%S"`
     JMC_ARGS="-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:FlightRecorderOptions=stackdepth=1024 -XX:StartFlightRecording=delay=1s,duration=99999s,disk=true,filename="$GROUP_ID"-$timestamp.jfr"
 else
     JMC_ARGS=""
 fi

echo "*************************************"
echo "GROUP_ID = "$GROUP_ID
echo "VERSION = "$VERSION
echo "JMC_ARGS = "$JMC_ARGS
echo "JAVA_HOME = "$JAVA_HOME
echo "*************************************"

echo ">>> START $GROUP_ID:$VERSION SERVICE <<<"
java $JMC_ARGS -jar ../$GROUP_ID/build/libs/$GROUP_ID-$VERSION.jar