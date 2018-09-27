package org.ib.vertx.hatserviceprovider;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.log4j.Logger;
import org.ib.vertx.microservicecommonblueprint.HttpServerManager;
import org.ib.vertx.microservicecommonblueprint.RestApiServiceDiscovery;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class HatVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(HatProviderApplication.class);
    public RestApiServiceDiscovery serviceDiscovery;
    private HttpServerManager serverManager;

    private static final String SERVICE_NAME = "hat-provider";

    private static final String API_PROVIDE_HAT = "/provideHat";
    private static final String API_HAT_MENU = "/hatMenu";

    @Override
    public void start(Future<Void> startFuture) {
        // Create a router object.
        Router router = Router.router(vertx);

        // Record endpoints.
        router.get(API_PROVIDE_HAT).handler(this::orderHat);
        router.get(API_HAT_MENU).handler(this::hatMenu);

        String host = config().getString("http.address", "localhost");
        int port = config().getInteger("http.port", 8080);

        // Create the Service Discovery endpoint
        serviceDiscovery = new RestApiServiceDiscovery(this);
        serverManager = new HttpServerManager(this);

        // create HTTP server and publish REST service
        serverManager.createHttpServer(router, host, port)
            .compose(serverCreated -> serviceDiscovery.publishHttpEndpoint(SERVICE_NAME, host, port))
            .setHandler(startFuture.completer());

        logger.info(HatVerticle.class.getName() + " Started");
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