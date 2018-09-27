package org.ib.vertx.hatserviceprovider;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.ServiceReference;
import io.vertx.servicediscovery.types.HttpEndpoint;
import org.apache.log4j.Logger;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class RestApiServiceDiscovery {

    private final static Logger logger = Logger.getLogger(RestApiServiceDiscovery.class);
    private final ServiceDiscovery discovery;

    public RestApiServiceDiscovery(Vertx vertx) {
        discovery = ServiceDiscovery.create(vertx, new ServiceDiscoveryOptions()
            .setBackendConfiguration(
                new JsonObject()
                    .put("host", "127.0.0.1")
                    .put("key", "records")
            ));
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
                logger.info("Endpoint " + publishedRecord.getLocation() + " publication succeeded");
            } else {
                // publication failed
                logger.warn("Endpoint " + root + " publication failed");
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

}
