package org.ib.vertx.vertxgateway;

import io.vertx.core.Vertx;

public class VertxGatewayApplication {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new VertxGatewayApiVerticle());
    }

}