package org.ib.vertx.httpclientshop;

import com.netflix.hystrix.HystrixCommand;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.servicediscovery.Record;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class HttpClientVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(HttpClientVerticle.class);
    public RestApiServiceDiscovery serviceDiscovery;

    @Override
    public void start(Future<Void> startFuture) {
        // Create a router object.
        Router router = Router.router(vertx);
        serviceDiscovery = new RestApiServiceDiscovery(vertx);

        // Record endpoints.
        router.get("/").handler(this::home);
        router.get("/orderHat").handler(this::orderHat);
        router.get("/orderShoe").handler(this::orderShoe);

        serviceDiscovery.publish("httpclient-shop1", "localhost", config().getInteger("http.port", 8081), "/");
        serviceDiscovery.publish("httpclient-shop2", "localhost", config().getInteger("http.port", 8081), "/orderHat");
        serviceDiscovery.publish("httpclient-shop3", "localhost", config().getInteger("http.port", 8081), "/orderShoe");

        // Create the HTTP server and pass the "accept" method to the request handler.
        vertx
            .createHttpServer()
            .requestHandler(router::accept)
            .listen(
                    // Retrieve the port from the configuration,
                    // default to 8081.
                    config().getInteger("http.port", 8081),
                    result -> {
                        if (result.succeeded()) {
                            startFuture.complete();
                        } else {
                            startFuture.fail(result.cause());
                        }
                    }
            );

        logger.info(HttpClientVerticle.class.getName() + " Started");
    }

    private void home(RoutingContext routingContext) {
        routingContext.response()
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(Json.encodePrettily(new HomePageHelp()));
    }

    private void orderShoe(RoutingContext routingContext) {
        performRestCall(routingContext, "/orderShoe");
    }

    private void orderHat(RoutingContext routingContext) {
        performRestCall(routingContext, "/orderHat");
        serviceDiscovery.getAllEndpoints().setHandler(ar -> {
            if (ar.succeeded()) {
                List<Record> recordList = ar.result();
                System.out.println("Service Discovery Endpoints:");
                for (Record rec : recordList)
                    System.out.println(rec.getLocation());
            } else {
                System.out.println("Nothing found");
            }
        });
    }

    private void performRestCall(RoutingContext routingContext, String requestURI){
        vertx.runOnContext(v -> {
            HystrixCommand<String> command = new RestApiHystrixCommand(vertx, 8080, "localhost", requestURI);
            vertx.<String>executeBlocking(
                    future -> future.complete(command.execute()),
                    ar -> {
                        // back on the event loop
                        String result = ar.result();
                        logger.info(result);
                        routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
                            .end(result);
                    }
            );
        });
    }

    @Override
    public void stop() {
        logger.info(HttpClientVerticle.class.getName() + " Stopped");
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