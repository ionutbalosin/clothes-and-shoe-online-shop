package org.ib.vertx.redisservicediscovery;

import redis.embedded.RedisServer;

public class RedisBackendService {

    private static RedisServer server;

    public static void main (String [] args) throws Exception {
        startRedis();
    }

    static public void startRedis() throws Exception {
        server = new RedisServer(6379);
        System.out.println("Created embedded redis server on port 6379");
        server.start();
    }

    static public void stopRedis() throws Exception {
        server.stop();
    }

}
