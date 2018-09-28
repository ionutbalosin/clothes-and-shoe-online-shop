package org.ib.vertx.hatserviceprovider;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;

public class HatProviderApplication {

    public static void main(String[] args) throws InterruptedException {
        Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(
            new DropwizardMetricsOptions()
                .setEnabled(true)
                .setJmxEnabled(true))
        );
       vertx.deployVerticle(new HatApiVerticle());
    }

}