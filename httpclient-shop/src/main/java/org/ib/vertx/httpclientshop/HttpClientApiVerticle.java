package org.ib.vertx.httpclientshop;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.log4j.Logger;
import org.ib.vertx.microservicecommonblueprint.RestApiHelperVerticle;

import java.util.ArrayList;
import java.util.List;

public class HttpClientApiVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(HttpClientApiVerticle.class);
    private RestApiHelperVerticle helperVerticle;

    private static final String SERVICE_NAME = "http-client-shop";
    private static final String API_NAME = "http-client-shop";

    private static final String API_ROOT = "/";
    private static final String API_ORDER_HAT = "/orderHat";
    private static final String API_ORDER_SHOE = "/orderShoe";

    @Override
    public void start(Future<Void> startFuture) {
        // Create a router object.
        Router router = Router.router(vertx);

        // Record endpoints.
        router.get(API_ROOT).handler(this::home);
        router.get(API_ORDER_HAT).handler(this::orderHat);
        router.get(API_ORDER_SHOE).handler(this::orderShoe);

        String host = config().getString("http.address", "localhost");
        int port = config().getInteger("http.port", 9091);

        // Create the Service Discovery endpoint and HTTPServerManager
        helperVerticle = new RestApiHelperVerticle(this);

        // create HTTP server and publish REST HTTP Endpoint
        helperVerticle.createHttpServer(router, host, port)
            .compose(serverCreated -> helperVerticle.publishHttpEndpoint(SERVICE_NAME, host, port, API_NAME))
            .setHandler(startFuture.completer());

        logger.info(HttpClientApiVerticle.class.getName()  + " started on port " + port);
    }

    @Override
    public void stop(Future<Void> future) {
        helperVerticle.stop(future);
    }

    private void home(RoutingContext routingContext) {
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(new HomePageHelp()));
    }

    private void orderShoe(RoutingContext routingContext) {
        helperVerticle.dispatchRequests(routingContext, "/shoe-provider/provideShoe");
    }

    private void orderHat(RoutingContext routingContext) {
        helperVerticle.dispatchRequests(routingContext, "/hat-provider/provideHat");
    }

    @Override
    public void stop() {
        logger.info(HttpClientApiVerticle.class.getName() + " stopped");
    }
}

class HomePageHelp {
    String description;
    List<EndpointDescription> endpoints;

    public HomePageHelp() {
        this.description = "Home Page Help";
        endpoints = new ArrayList<>();
        endpoints.add(new EndpointDescription("/orderHat", "to order a hat"));
        endpoints.add(new EndpointDescription("/orderShoe", "to order shoe"));
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<EndpointDescription> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<EndpointDescription> endpoints) {
        this.endpoints = endpoints;
    }
}

class EndpointDescription {

    String endpoint;
    String description;

    public EndpointDescription(String endpoint, String description) {
        this.endpoint = endpoint;
        this.description = description;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getDescription() {
        return description;
    }
}