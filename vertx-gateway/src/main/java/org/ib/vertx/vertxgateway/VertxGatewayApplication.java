package org.ib.vertx.vertxgateway;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;

public class VertxGatewayApplication {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(
            new DropwizardMetricsOptions()
                .setEnabled(true)
                .setJmxEnabled(true))
        );
        vertx.deployVerticle(new VertxGatewayApiVerticle());
    }

}