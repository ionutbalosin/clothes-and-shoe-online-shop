package org.ib.vertx.httpclientshop;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.dropwizard.MetricsService;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.log4j.Logger;
import org.ib.vertx.microservicecommonblueprint.RestApiHelperVerticle;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class HttpClientApiVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(HttpClientApiVerticle.class);
    private RestApiHelperVerticle helperVerticle;
    private MetricsService metricsService;

    private static final String SERVICE_NAME = "http-client-shop";
    private static final String API_NAME = "http-client-shop";

    private static final String API_ROOT = "/";
    private static final String API_ORDER_HAT = "/orderHat";
    private static final String API_ORDER_SHOE = "/orderShoe";
    private static final String API_PROVIDE_METRICS = "/metrics";

    @Override
    public void start(Future<Void> startFuture) {
        // Create a router object.
        Router router = Router.router(vertx);

        // Record endpoints.
        router.get(API_ROOT).handler(this::home);
        router.get(API_ORDER_HAT).handler(this::orderHat);
        router.get(API_ORDER_SHOE).handler(this::orderShoe);
        router.get(API_PROVIDE_METRICS).handler(this::metrics);

        String serviceName = config().getString("api.name", SERVICE_NAME);
        String apiName = config().getString("service.name", API_NAME);
        String host = config().getString("http.address", "localhost");
        int port = config().getInteger("http.port", 9091);

        // Create the Service Discovery endpoint and HTTPServerManager
        helperVerticle = new RestApiHelperVerticle(this);

        // create HTTP server and publish REST HTTP Endpoint
        helperVerticle.createHttpServer(router, host, port)
            .compose(serverCreated -> helperVerticle.publishHttpEndpoint(serviceName, host, port, apiName))
            .setHandler(startFuture.completer());

        // Create the metrics service which returns a snapshot of measured objects
        metricsService = MetricsService.create(vertx);

        logger.info(HttpClientApiVerticle.class.getName()  + " started on port " + port);
        logger.info("JVM running for " + (ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0) + " sec");
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

    private void metrics(RoutingContext routingContext) {
        JsonObject metrics = metricsService.getMetricsSnapshot(vertx);
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(metrics));
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