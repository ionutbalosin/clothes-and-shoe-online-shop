package org.ib.vertx.microservicecommonblueprint;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;

public class HttpServerManager {

    private final AbstractVerticle verticle;

    public HttpServerManager(AbstractVerticle verticle) {
        this.verticle = verticle;
    }

    public Future<Void> createHttpServer(Router router, String host, int port) {
        Future<HttpServer> httpServerFuture = Future.future();
        verticle.getVertx().createHttpServer()
            .requestHandler(router::accept)
            .listen(
                    port,
                    host,
                    result -> {
                        if (result.succeeded()) {
                            httpServerFuture.complete();
                        } else {
                            httpServerFuture.fail(result.cause());
                        }
                    }
            );
        return httpServerFuture.map(r -> null);
    }
}
