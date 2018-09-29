package org.ib.vertx.hatserviceprovider;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import org.apache.log4j.Logger;
import rx.functions.Action1;

import static org.ib.vertx.microservicecommonblueprint.ConfigRetrieverHelper.CONFIG_RETRIEVER_HELPER;

public class HatProviderApplication {

    private final static Logger logger = Logger.getLogger(HatProviderApplication.class);
    private static Vertx vertx;

    public static void main(String[] args) {

        logger.info("Java Version [" + System.getProperty("java.version") + "]");

        vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(
            new DropwizardMetricsOptions()
                .setEnabled(true)
                .setJmxEnabled(true))
        );

        CONFIG_RETRIEVER_HELPER
            .fromFileStore("application.json")
            .fromSystem()
            .createConfig(vertx)
            .subscribe(configReady);
    }

    public static Action1<JsonObject> configReady = config -> {
        vertx.deployVerticle(new HatApiVerticle(), new DeploymentOptions().setConfig(config));
    };
}