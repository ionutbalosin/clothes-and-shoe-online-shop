package org.ib.vertx.hatserviceprovider;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

public class HatProviderApplication {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new HatVerticle());
    }

}