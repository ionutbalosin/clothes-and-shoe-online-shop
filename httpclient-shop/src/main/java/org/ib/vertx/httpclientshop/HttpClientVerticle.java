package org.ib.vertx.httpclientshop;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class HttpClientVerticle extends AbstractVerticle {

    public final static Logger logger = Logger.getLogger(HttpClientVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        // Create a router object.
        Router router = Router.router(vertx);
        router.get("/").handler(this::home);
        router.get("/orderHat").handler(this::orderHat);
        router.get("/orderShoe").handler(this::orderShoe);

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
        vertx.runOnContext(v -> {
            HystrixCommand<String> command = getHystrixCommand(vertx, routingContext, "Shoe");
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

    private void orderHat(RoutingContext routingContext) {
        vertx.runOnContext(v -> {
            HystrixCommand<String> command = getHystrixCommand(vertx, routingContext, "Hat");
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

    private HystrixCommand<String> getHystrixCommand(Vertx vertx, RoutingContext routingContext, String service) {
        return new RestHystrixCommand(vertx, routingContext, service);
    }

    @Override
    public void stop() {
        logger.info(HttpClientVerticle.class.getName() + " Stopped");
    }
}

class RestHystrixCommand extends HystrixCommand<String> {

    protected Vertx vertx;
    protected RoutingContext routingContext;
    public final static Logger logger = Logger.getLogger(RestHystrixCommand.class);
    public final String service;

    public RestHystrixCommand(Vertx vertx, RoutingContext routingContext, String service) {
        super(HystrixCommandGroupKey.Factory.asKey("RestHystrixCommand"));
        this.routingContext = routingContext;
        this.vertx = vertx;
        this.service = service;
    }

    @Override
    protected String getFallback() {
        return service + " service is not available. Please check again later :(!";
    }

    @Override
    protected String run() throws InterruptedException {
        AtomicReference<String> result = new AtomicReference<>();
        HttpClient client = vertx.createHttpClient();
        // NB: Trick to wait for the asynchronous thread with the HTTP response until sending a Client response
        CountDownLatch latch = new CountDownLatch(1);

        Handler<Throwable> errorHandler = t -> {
            latch.countDown();
        };

        client.get(8080, "localhost", "/order" + service, response -> {
            response.exceptionHandler(errorHandler);
            if (response.statusCode() != 200) {
                logger.error("Fail");
                latch.countDown();
            } else {
                // Create an empty buffer
                Buffer totalBuffer = Buffer.buffer();

                response.handler( buffer -> {
                    totalBuffer.appendBuffer(buffer);
                });

                response.endHandler( handler -> {
                    // Now all the body has been read
                    String responseString = String.format("[HttpClientShop-%d] - %s", ThreadLocalRandom.current().nextInt(), totalBuffer.toString());
                    result.set(responseString);
                    latch.countDown();
                });

            }
        })
        .exceptionHandler(errorHandler)
        .end();

        latch.await();

        if (result.get() == null) {
            throw new RuntimeException("Failed to retrieve the HTTP response");
        } else {
            return result.get();
        }
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