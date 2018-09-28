package org.ib.vertx.vertxgateway;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.log4j.Logger;
import org.ib.vertx.microservicecommonblueprint.HttpServerManager;
import org.ib.vertx.microservicecommonblueprint.RestApiServiceDiscovery;

public class VertxGatewayVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(VertxGatewayVerticle.class);
    private RestApiServiceDiscovery serviceDiscovery;
    private HttpServerManager serverManager;

    private static final String SERVICE_NAME = "vertx-gateway";
    private static final String API_NAME = "vertx-gateway";

    private static final String API_ROOT = "/*";

    @Override
    public void start(Future<Void> startFuture) {
        // Create a router object.
        Router router = Router.router(vertx);

        // Record endpoints.
        router.get(API_ROOT).handler(this::dispatch);

        String host = config().getString("http.address", "localhost");
        int port = config().getInteger("http.port", 8771);

        // Create the Service Discovery endpoint and HTTPServerManager
        serviceDiscovery = new RestApiServiceDiscovery(this);
        serverManager = new HttpServerManager(this);

        // create HTTP server and publish REST service
        serverManager.createHttpServer(router, host, port)
            .compose(serverCreated -> serviceDiscovery.publishHttpEndpoint(SERVICE_NAME, host, port, API_NAME))
            .setHandler(startFuture.completer());

        logger.info(VertxGatewayVerticle.class.getName() + " started on port " + port);
    }

    private void dispatch(RoutingContext routingContext) {
        String uriPath = routingContext.request().uri();
        logger.info("Dispatching request for uri " + uriPath);
        serviceDiscovery.dispatchRequests(routingContext, uriPath);
    }

    @Override
    public void stop() {
        logger.info(VertxGatewayVerticle.class.getName() + " Stopped");
    }
}
