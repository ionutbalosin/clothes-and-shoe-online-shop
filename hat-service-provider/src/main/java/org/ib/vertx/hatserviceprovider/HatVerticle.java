package org.ib.vertx.hatserviceprovider;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class HatVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(HatProviderApplication.class);

    @Override
    public void start(Future<Void> startFuture) {

        // Create a router object.
        Router router = Router.router(vertx);
        router.get("/orderHat").handler(this::orderHat);
        router.get("/hatMenu").handler(this::hatMenu);

        // Create the HTTP server and pass the "accept" method to the request handler.
        vertx
            .createHttpServer()
            .requestHandler(router::accept)
            .listen(
                    // Retrieve the port from the configuration,
                    // default to 8080.
                    config().getInteger("http.port", 8080),
                    result -> {
                        if (result.succeeded()) {
                            startFuture.complete();
                        } else {
                            startFuture.fail(result.cause());
                        }
                    }
            );

        logger.info("Verticle Started");
    }

    private void hatMenu(RoutingContext routingContext) {
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(Arrays.asList(new Hat("RedHat", "8 Euro"), new Hat("YellowHat", "8 Euro"))));
    }

    private void orderHat(RoutingContext routingContext) {
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(String.format("[HatProvider-%d] - %s", ThreadLocalRandom.current().nextInt(), "RedHat"));
    }

    @Override
    public void stop() throws Exception {
        logger.info("Verticle Stopped");
    }
}