package org.ib.vertx.microservicecommonblueprint;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
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
import java.util.concurrent.atomic.AtomicReference;

public class RestApiServiceDiscovery {

    private final static Logger logger = Logger.getLogger(RestApiServiceDiscovery.class);
    private final Set<Record> registeredRecords;
    private final AbstractVerticle verticle;
    private ServiceDiscovery discovery;
    //private CircuitBreaker circuitBreaker;

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
        //JsonObject cbOptions = verticle.config().getJsonObject("circuit-breaker") != null ?
        //verticle.config().getJsonObject("circuit-breaker") : new JsonObject();
        //circuitBreaker = CircuitBreaker.create(cbOptions.getString("name", "circuit-breaker"), verticle.getVertx(),
        //    new CircuitBreakerOptions()
        //        .setMaxFailures(cbOptions.getInteger("max-failures", 5))
        //        .setTimeout(cbOptions.getLong("timeout", 10000L))
        //        .setFallbackOnFailure(true)
        //        .setResetTimeout(cbOptions.getLong("reset-timeout", 30000L))
        //);
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

    public Record publish(String name, String host, int port, String root){
        // Record creation from a type
        Record record = HttpEndpoint.createRecord(
                name, // The service name
                host, // The host
                port, // the port
                root // the root of the service
        );

        discovery.publish(record, ar -> {
            if (ar.succeeded()) {
                // publication succeeded
                Record publishedRecord = ar.result();
                logger.info("Endpoint [" + name + "] " + publishedRecord.getLocation() + " publication succeeded");
            } else {
                // publication failed
                logger.warn("Endpoint [" + name + "] publication failed. Please check if the back-end is up and running.");
            }
        });

        return record;
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

}
