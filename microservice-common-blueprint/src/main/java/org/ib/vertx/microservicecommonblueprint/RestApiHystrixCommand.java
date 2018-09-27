package org.ib.vertx.microservicecommonblueprint;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import org.apache.log4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class RestApiHystrixCommand extends HystrixCommand<String> {

    private final static Logger logger = Logger.getLogger(RestApiHystrixCommand.class);

    private final Vertx vertx;
    private final int port;
    private final String host;
    private final String requestURI;

    public RestApiHystrixCommand(Vertx vertx, int port, String host, String requestURI) {
        super(HystrixCommandGroupKey.Factory.asKey("RestHystrixCommand"));
        this.vertx = vertx;
        this.port = port;
        this.host = host;
        this.requestURI = requestURI;
    }

    @Override
    protected String getFallback() {
        return requestURI + " endpoint is not available. Please check again later :(!";
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

        client.get(port, host, requestURI, response -> {
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