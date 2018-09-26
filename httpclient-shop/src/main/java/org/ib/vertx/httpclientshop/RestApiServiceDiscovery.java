package org.ib.vertx.httpclientshop;

import io.vertx.core.Vertx;
import io.vertx.reactivex.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import org.apache.log4j.Logger;

public class RestApiServiceDiscovery {

    private final static Logger logger = Logger.getLogger(RestApiServiceDiscovery.class);

    public RestApiServiceDiscovery(Vertx vertx) {
        this.vertx = vertx;
    }

    private final Vertx vertx;

    public Record publish(String name, String host, int port, String root){
        ServiceDiscovery discovery = ServiceDiscovery.create(vertx);
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
                logger.info("Endpoint " + publishedRecord.getName()+ " publication succeeded");
            } else {
                // publication failed
                logger.warn("Endpoint " + record.getName()+ " publication failed");
            }
        });

        return record;
    }

}
