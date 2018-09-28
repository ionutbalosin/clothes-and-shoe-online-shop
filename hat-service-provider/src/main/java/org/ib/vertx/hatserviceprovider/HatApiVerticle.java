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

public class HatApiVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(HatProviderApplication.class);
    public RestApiServiceDiscovery serviceDiscovery;
    private HttpServerManager serverManager;

    private static final String SERVICE_NAME = "hat-provider";
    private static final String API_NAME = "hat-provider";

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
        int port = config().getInteger("http.port", 9081);

        // Create the Service Discovery endpoint
        serviceDiscovery = new RestApiServiceDiscovery(this);
        serverManager = new HttpServerManager(this);

        // create HTTP server and publish REST HTTP Endpoint
        serverManager.createHttpServer(router, host, port)
            .compose(serverCreated -> serviceDiscovery.publishHttpEndpoint(SERVICE_NAME, host, port, API_NAME))
            .setHandler(startFuture.completer());

        logger.info(HatApiVerticle.class.getName()  + " started on port " + port);
    }

    private void hatMenu(RoutingContext routingContext) {
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(Arrays.asList(new Hat("RedHat", "80 Euro"), new Hat("YellowHat", "60 Euro"))));
    }

    private void orderHat(RoutingContext routingContext) {
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(String.format("[HatProvider][%d]-%s", ThreadLocalRandom.current().nextInt(9999), "RedHat"));
    }

    @Override
    public void stop() throws Exception {
        logger.info(HatApiVerticle.class.getName() + " stopped");
    }
}