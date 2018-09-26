package org.ib.vertx.httpclientshop;

import io.vertx.core.Vertx;

public class HttpClientApplication {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new HttpClientVerticle());
    }

}