package org.ib.vertx.vertxgateway;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.log4j.Logger;
import org.ib.vertx.microservicecommonblueprint.RestApiHelperVerticle;

public class VertxGatewayApiVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(VertxGatewayApiVerticle.class);
    private RestApiHelperVerticle helperVerticle;

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
        helperVerticle = new RestApiHelperVerticle(this);

        // create HTTP server and publish REST HTTP Endpoint
        helperVerticle.createHttpServer(router, host, port)
            .compose(serverCreated -> helperVerticle.publishHttpEndpoint(SERVICE_NAME, host, port, API_NAME))
            .setHandler(startFuture.completer());

        logger.info(VertxGatewayApiVerticle.class.getName() + " started on port " + port);
    }

    @Override
    public void stop(Future<Void> future) {
        helperVerticle.stop(future);
    }

    private void dispatch(RoutingContext routingContext) {
        String uriPath = routingContext.request().uri();
        logger.debug("Dispatching request for uri " + uriPath);
        helperVerticle.dispatchRequests(routingContext, uriPath);
    }

    @Override
    public void stop() {
        logger.info(VertxGatewayApiVerticle.class.getName() + " stopped");
    }
}
