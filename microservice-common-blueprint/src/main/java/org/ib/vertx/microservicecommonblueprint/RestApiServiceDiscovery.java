package org.ib.vertx.microservicecommonblueprint;

import com.netflix.hystrix.HystrixCommand;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.ServiceReference;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class RestApiServiceDiscovery {

    private final static Logger logger = Logger.getLogger(RestApiServiceDiscovery.class);
    private final Set<Record> registeredRecords;
    private final AbstractVerticle verticle;
    private ServiceDiscovery discovery;
    private CircuitBreaker circuitBreaker;

    public RestApiServiceDiscovery(AbstractVerticle verticle) {
        this.verticle = verticle;
        registeredRecords = new ConcurrentHashSet<>();
    }

    public Future<Void> publishHttpEndpoint(String name, String host, int port, String apiName) {
        Record record = HttpEndpoint.createRecord(name, host, port, "/",
            new JsonObject().put("api.name", verticle.config().getString("api.name", apiName))
        );
        return publish(record);
    }

    private Future<Void> publish(Record record) {
        if (discovery == null) {
            try {
                start();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create discovery service");
            }
        }

        Future<Void> future = Future.future();
        // publish the service
        discovery.publish(record, ar -> {
            if (ar.succeeded()) {
                registeredRecords.add(record);
                logger.info("Service <" + ar.result().getName() + "> published");
                future.complete();
            } else {
                future.fail(ar.cause());
            }
        });

        return future;
    }

    private void start() throws Exception {
        // init service discovery instance
        discovery = ServiceDiscovery.create(verticle.getVertx(), new ServiceDiscoveryOptions()
            .setBackendConfiguration(
                new JsonObject()
                    .put("host", "127.0.0.1")
                    .put("key", "records")
            ));

        // init circuit breaker instance
        JsonObject cbOptions = verticle.config().getJsonObject("circuit-breaker") != null ?
        verticle.config().getJsonObject("circuit-breaker") : new JsonObject();
        circuitBreaker = CircuitBreaker.create(cbOptions.getString("name", "circuit-breaker"), verticle.getVertx(),
            new CircuitBreakerOptions()
                .setMaxFailures(cbOptions.getInteger("max-failures", 5))
                .setTimeout(cbOptions.getLong("timeout", 10000L))
                .setFallbackOnFailure(true)
                .setResetTimeout(cbOptions.getLong("reset-timeout", 30000L))
        );
    }

    private void stop(Future<Void> future) throws Exception {
        // In current design, the publisher is responsible for removing the service
        List<Future> futures = new ArrayList<>();
        registeredRecords.forEach(record -> {
            Future<Void> cleanupFuture = Future.future();
            futures.add(cleanupFuture);
            discovery.unpublish(record.getRegistration(), cleanupFuture.completer());
        });

        if (futures.isEmpty()) {
            discovery.close();
            future.complete();
        } else {
            CompositeFuture.all(futures)
                .setHandler(ar -> {
                    discovery.close();
                    if (ar.failed()) {
                        future.fail(ar.cause());
                    } else {
                        future.complete();
                    }
                });
        }
    }

    public void dispatchRequests(RoutingContext context, String path) {
        int initialOffset = 1; // length of `/`
        // run with circuit breaker in order to deal with failure
        circuitBreaker.execute(future -> {
            getAllEndpoints().setHandler(ar -> {
                if (ar.succeeded()) {
                    List<Record> recordList = ar.result();
                    // get relative path and retrieve prefix to dispatch client
                    String prefix = (path.substring(initialOffset).split("/"))[0];
                    // generate new relative path
                    String newPath = path.substring(initialOffset + prefix.length());
                    // get one relevant HTTP client, may not exist
                    Optional<Record> client = recordList.stream()
                        .filter(record -> record.getMetadata().getString("api.name") != null)
                        .filter(record -> record.getMetadata().getString("api.name").equals(prefix))
                        .findAny(); // simple load balance
                    logger.debug("Creating request to path=[" + path + "] and prefix=[" + prefix + "] and newPath=[" + newPath + "]");
                    if (client.isPresent()) {
                        logger.info("Dispatching request to [" + client.get().getLocation() + "] for path=[" + newPath + "]");
                        doDispatch(context, newPath, discovery.getReference(client.get()).get(), future);
                    } else {
                        logger.warn("Client for path [" + path + "] not found");
                        notFound(context);
                        future.complete();
                    }
                } else {
                    future.fail(ar.cause());
                }
            });
        }).setHandler(ar -> {
            if (ar.failed()) {
                badGateway(ar.cause(), context);
            }
        });
    }

    private void doDispatch(RoutingContext context, String path, HttpClient client, Future<Object> cbFuture) {
        HttpClientRequest toReq = client
            .request(context.request().method(), path, response -> {
                response.bodyHandler(body -> {
                    if (response.statusCode() >= 500) { // api endpoint server error, circuit breaker should fail
                        cbFuture.fail(response.statusCode() + ": " + body.toString());
                    } else {
                        HttpServerResponse toRsp = context.response()
                            .setStatusCode(response.statusCode());
                        response.headers().forEach(header -> {
                            toRsp.putHeader(header.getKey(), header.getValue());
                        });
                        String bodyOutput = String.format("[HttpClientShop-%d] - %s", ThreadLocalRandom.current().nextInt(), body.toString());
                        logger.info("Received " + bodyOutput);
                        // send response
                        toRsp.putHeader("content-type", "text/plain");
                        toRsp.end(bodyOutput);
                        cbFuture.complete();
                    }
                    ServiceDiscovery.releaseServiceObject(discovery, client);
                });
            });
        // set headers
        context.request().headers().forEach(header -> {
            toReq.putHeader(header.getKey(), header.getValue());
        });
        if (context.user() != null) {
            toReq.putHeader("user-principal", context.user().principal().encode());
        }
        // send request
        if (context.getBody() == null) {
            toReq.end();
        } else {
            toReq.end(context.getBody());
        }
    }

    private void dispatchRequests2(RoutingContext routingContext, String requestURI){
        verticle.getVertx().runOnContext(v -> {
            HystrixCommand<String> command = new RestApiHystrixCommand(verticle.getVertx(), 8080, "localhost", requestURI);
            verticle.getVertx().<String>executeBlocking(
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

    public void unPublish(Record record){
        discovery.unpublish(record.getRegistration(), ar -> {
            if (ar.succeeded()) {
                logger.info("Endpoint " + record.getLocation() + " successfully unpublished");
            } else {
                logger.warn("Endpoint " + record.getLocation() + " failed to unpublish");
            }
        });
    }

    public Optional<HttpClient> getService(String name){
        final AtomicReference<HttpClient> httpClient = new AtomicReference<>();

        discovery.getRecord(new JsonObject().put("name", name), ar -> {
            if (ar.succeeded()) {
                if (ar.result() != null) {
                    // we have a record
                    logger.info("Found " + ar.result().getLocation());
                    ServiceReference reference = discovery.getReference(ar.result());
                    httpClient.set(reference.getAs(HttpClient.class));
                } else {
                    // the lookup succeeded, but no matching service
                    logger.warn("Endpoint lookup succeeded, but no matching service");
                }
            } else {
                // lookup failed
                logger.info("Endpoint lookup failed");
            }
        });

        return Optional.of(httpClient.get());
    }

    public Future<List<Record>> getAllEndpoints() {
        Future<List<Record>> future = Future.future();
        discovery.getRecords(record -> record.getType().equals(HttpEndpoint.TYPE), future.completer());
        return future;
    }

    // helper method dealing with failure

    protected void badRequest(RoutingContext context, Throwable ex) {
        context.response().setStatusCode(400)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("error", ex.getMessage()).encodePrettily());
    }

    protected void notFound(RoutingContext context) {
        context.response().setStatusCode(404)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("message", "not_found").encodePrettily());
    }

    protected void internalError(RoutingContext context, Throwable ex) {
        context.response().setStatusCode(500)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("error", ex.getMessage()).encodePrettily());
    }

    protected void notImplemented(RoutingContext context) {
        context.response().setStatusCode(501)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("message", "not_implemented").encodePrettily());
    }

    protected void badGateway(Throwable ex, RoutingContext context) {
        ex.printStackTrace();
        context.response()
            .setStatusCode(502)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("error", "bad_gateway")
                    //.put("message", ex.getMessage())
                    .encodePrettily());
    }

    protected void serviceUnavailable(RoutingContext context) {
        context.fail(503);
    }

    protected void serviceUnavailable(RoutingContext context, Throwable ex) {
        context.response().setStatusCode(503)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("error", ex.getMessage()).encodePrettily());
    }

    protected void serviceUnavailable(RoutingContext context, String cause) {
        context.response().setStatusCode(503)
                .putHeader("content-type", "application/json")
                .end(new JsonObject().put("error", cause).encodePrettily());
    }

}
