package org.ib.vertx.microservicecommonblueprint;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.log4j.Logger;
import rx.Observable;

import java.util.Objects;
import java.util.Optional;

public enum ConfigRetrieverHelper {
    CONFIG_RETRIEVER_HELPER;

    public final static Logger logger = Logger.getLogger(ConfigRetrieverHelper.class);

    private ConfigRetriever configRetriever;
    private ConfigRetrieverOptions options = new ConfigRetrieverOptions();

    public Observable<JsonObject> createConfig(final Vertx vertx) {
        configRetriever = ConfigRetriever.create(vertx, options);

        Observable<JsonObject> configObservable = Observable.create(subscriber -> {
            configRetriever.getConfig(ar -> {
                if (ar.failed()) {
                    logger.warn("Failed to retrieve configuration");
                } else {
                    logger.info("Successfully retrieved configuration");
                    final JsonObject config =
                        vertx.getOrCreateContext().config().mergeIn(
                            Optional.ofNullable(ar.result()).orElse(new JsonObject()));
                    logger.debug("Configuration " + config);
                    subscriber.onNext(config);
                }
            });

            configRetriever.listen(ar -> {
                logger.info("Received configuration changes");
                final JsonObject config =
                    vertx.getOrCreateContext().config().mergeIn(
                        Optional.ofNullable(ar.getNewConfiguration()).orElse(new JsonObject()));
                logger.debug("Configuration " + config);
                // TODO: Take into account these updates later on!
                //subscriber.onNext(config);
            });
        });

        configObservable.onErrorReturn(t -> {
            logger.error("Failed to retrieve configuration!", t);
            return null;
        });

        return configObservable.filter(Objects::nonNull);
    }

    public ConfigRetrieverHelper fromFileStore(final String path) {
        ConfigStoreOptions fileStore = new ConfigStoreOptions()
            .setType("file")
            .setConfig(new JsonObject().put("path", path));
        options.addStore(fileStore);
        return this;
    }

    public ConfigRetrieverHelper fromSystem() {
        ConfigStoreOptions sysStore = new ConfigStoreOptions()
            .setType("sys")
            .setConfig(new JsonObject().put("cache", false));
        options.addStore(sysStore);
        return this;
    }
}