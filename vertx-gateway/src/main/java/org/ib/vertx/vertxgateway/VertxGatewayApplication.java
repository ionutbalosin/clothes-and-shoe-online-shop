package org.ib.vertx.vertxgateway;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class VertxGatewayApplication {

    public static void main(String[] args) {
        final VertxOptions options = new VertxOptions().setEventLoopPoolSize(8);
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new VertxGatewayApiVerticle());
    }

}