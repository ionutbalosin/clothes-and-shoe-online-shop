package org.ib.vertx.redisservicediscovery;

import org.apache.log4j.Logger;
import redis.embedded.RedisServer;

import java.lang.management.ManagementFactory;

public class RedisBackendService {

    private final static Logger logger = Logger.getLogger(RedisBackendService.class);
    private static RedisServer server;

    public static void main (String [] args) throws Exception {
        logger.info("Java Version [" + System.getProperty("java.version") + "]");
        startRedis();
    }

    static public void startRedis() throws Exception {
        int port = Integer.getInteger(System.getProperty("http.port"), 8761);
        server = new RedisServer(port);
        server.start();
        logger.info("Embedded Redis server started on port " + port);
        logger.info("JVM running for " + (ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0) + " sec");
    }

    static public void stopRedis() throws Exception {
        server.stop();
    }

}
