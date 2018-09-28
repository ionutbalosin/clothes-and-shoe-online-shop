package org.ib.vertx.redisservicediscovery;

import org.apache.log4j.Logger;
import redis.embedded.RedisServer;

public class RedisBackendService {

    private final static Logger logger = Logger.getLogger(RedisBackendService.class);
    private static RedisServer server;

    public static void main (String [] args) throws Exception {
        startRedis();
    }

    static public void startRedis() throws Exception {
        int port = Integer.getInteger(System.getProperty("http.port"), 6379);
        server = new RedisServer(port);
        logger.info("Embedded Redis server started on port " + port);
        server.start();
    }

    static public void stopRedis() throws Exception {
        server.stop();
    }

}
