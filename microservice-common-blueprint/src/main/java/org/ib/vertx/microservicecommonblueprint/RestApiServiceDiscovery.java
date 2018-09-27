package org.ib.vertx.microservicecommonblueprint;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
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
import java.util.concurrent.CountDownLatch;
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

    public Future<Void> publishHttpEndpoint(String name, String host, int port) {
        Record record = HttpEndpoint.createRecord(name, host, port, "/",
            new JsonObject().put("api.name", verticle.config().getString("api.name", ""))
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

    public Buffer dispatchRequests(RoutingContext context, String path) {
        AtomicReference<Buffer> result = new AtomicReference<>();
        // NB: Trick to wait for the asynchronous thread with the HTTP response until sending a Client response
        CountDownLatch latch = new CountDownLatch(1);
        // run with circuit breaker in order to deal with failure
        circuitBreaker.execute(future -> {
            getAllEndpoints().setHandler(ar -> {
                if (ar.succeeded()) {
                    List<Record> recordList = ar.result();
                    // get relative path and retrieve prefix to dispatch client
                    logger.info("Creating request to URI " + path);

                    String prefix = (path.split("/"))[0];
                    // generate new relative path
                    String newPath = path.substring(prefix.length());
                    // get one relevant HTTP client, may not exist
                    Optional<Record> client = recordList.stream()
                        .filter(record -> record.getMetadata().getString("api.name") != null)
                        .filter(record -> record.getMetadata().getString("api.name").equals(prefix))
                        .findAny(); // simple load balance
                    if (client.isPresent()) {
                        logger.info("Creating request to " + client.get().getLocation());
                        result.set(doDispatch(context, newPath, discovery.getReference(client.get()).get(), future));
                    } else {
                        logger.info("Client not found");
                        notFound(context);
                        future.complete();
                    }
                } else {
                    future.fail(ar.cause());
                }
                latch.countDown();
            });
        }).setHandler(ar -> {
            if (ar.failed()) {
                badGateway(ar.cause(), context);
                latch.countDown();
            }
        });

        //try {
        //    latch.await();
        //} catch (InterruptedException e) {
        //    e.printStackTrace();
        //}

        return result.get();
    }

    /**
     * Dispatch the request to the downstream REST layers.
     *
     * @param context routing context instance
     * @param path    relative path
     * @param client  relevant HTTP client
     */
    private Buffer doDispatch(RoutingContext context, String path, HttpClient client, Future<Object> cbFuture) {
        AtomicReference<Buffer> result = new AtomicReference<>();
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
                        logger.info("Received " + body);
                        result.set(body);
                        // send response
                        toRsp.end(body);
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

        return result.get();
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
