package org.ib.vertx.hatserviceprovider;

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
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class HatApiVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(HatProviderApplication.class);
    private RestApiHelperVerticle helperVerticle;
    private MetricsService metricsService;

    private static final String SERVICE_NAME = "hat-provider";
    private static final String API_NAME = "hat-provider";

    private static final String API_PROVIDE_HAT = "/provideHat";
    private static final String API_HAT_MENU = "/hatMenu";
    private static final String API_PROVIDE_METRICS = "/metrics";

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        super.start();

        // Create a router object.
        Router router = Router.router(vertx);

        // Record endpoints.
        router.get(API_PROVIDE_HAT).handler(this::orderHat);
        router.get(API_HAT_MENU).handler(this::hatMenu);
        router.get(API_PROVIDE_METRICS).handler(this::metrics);

        String serviceName = config().getString("api.name", SERVICE_NAME);
        String apiName = config().getString("service.name", API_NAME);
        String host = config().getString("http.address", "localhost");
        int port = config().getInteger("http.port", 9081);

        // Create the Service Discovery endpoint
        helperVerticle = new RestApiHelperVerticle(this);

        // create HTTP server and publish REST HTTP Endpoint
        helperVerticle.createHttpServer(router, host, port)
            .compose(serverCreated -> helperVerticle.publishHttpEndpoint(serviceName, host, port, apiName))
            .setHandler(startFuture.completer());

        // Create the metrics service which returns a snapshot of measured objects
        metricsService = MetricsService.create(vertx);

        logger.info(HatApiVerticle.class.getName()  + " started on port " + port);
        logger.info("JVM running for " + (ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0) + " sec");
    }

    @Override
    public void stop(Future<Void> future) {
        helperVerticle.stop(future);
    }

    private void hatMenu(RoutingContext routingContext) {
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(Arrays.asList(new Hat("RedHat", "80 Euro"), new Hat("YellowHat", "60 Euro"))));
    }

    private void orderHat(RoutingContext routingContext) {
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
                .end(String.format("[HatProvider-%s][ResponseId-%d]-%s", Thread.currentThread().getName(), ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE), "RedHat"));
    }

    private void metrics(RoutingContext routingContext) {
        JsonObject metrics = metricsService.getMetricsSnapshot(vertx);
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(metrics));
    }

    @Override
    public void stop() throws Exception {
        logger.info(HatApiVerticle.class.getName() + " stopped");
    }
}