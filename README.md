## Food And Drinks Online Shop

### Description

This project is supposed to be a network of Clients (i.e. clothes and shoe Consumers) and Producers (i.e. clothes and shoe Producers), where Clients ask for clothes and shoe and the orders are provided by the Producers.
The entire communication is based on REST calls.

### Technical Details

1. Service Discovery for loosely coupling the Clients and Producers

### Technology Stack

1. Vertx HttpClient – REST client
2. Vertx Service Discovery - Redis backend for registration and discovery
3. Vertx Circuit Breaker
4. Vertx Dropwizard Metrics - for reporting metrics to /metrics endpoint
5. Vertx Config - for reading external config (file or system properties)
6. Vertx Gateway – an in-house gateway service implementation

### Build

´$ ./gradlew clean build ShadowJar´

### Run

```
$ cd scripts/
$ ./bootstrap-service.sh <GROUP_ID> <VERSION> [useJMC]
$ ./bootstrap-service.sh <GROUP_ID> <VERSION> [true||false]
```

Examples of starting all services with Java Mission Control (JMC) as optional parameter:
```
$ ./bootstrap-service.sh redis-service-discovery 0.0.1-SNAPSHOT [true]
$ ./bootstrap-service.sh vertx-gateway 0.0.1-SNAPSHOT [true]
$ ./bootstrap-service.sh hat-service-provider 0.0.1-SNAPSHOT [true]
$ ./bootstrap-service.sh httpclient-shop 0.0.1-SNAPSHOT [true]
```

To start each of these services on a different port, please specify -Dhttp.port=<port> in the shell script!

### Smoke Test

For checking the metrics, open a browser and check below URLs:
+ http://localhost:9081/metrics 
    - to access hat-service-provider service
+ http://localhost:9091/metrics 
    - to access httpclient-shop service
+ http://localhost:8771/metrics 
    - to access vertx-gateway service

To send real requests across micro-services, open a browser and check below URLs:
+ http://localhost:9081/provideHat 
    - request route: hat-service-provider
+ http://localhost:9091/orderHat
    - request route: httpclient-shop -> hat-service-provider
+ http://localhost:8771/http-client-shop/orderHat 
    - request route: vertx-gateway -> httpclient-shop -> hat-service-provider

### Load Test

Please first install *ab - Apache HTTP server benchmarking tool*

Then, open a terminal and launch 1000 requests (4 concurrent):
```
$ cd <ab_path>/bin
$ ./ab.exe -n 10000 -c 10 -l http://localhost:8771/http-client-shop/orderHat
```

### ToDo

1. Load Balancer - in-house implementation (nothing on the market)
2. Docker - Docker-ize all services
3. Tracing
    - in-house implementation (nothing on the market)
4. Monitoring
    - Prometheus
    - Grafana
    - Kibana
5. Security
    - KeyCloak – identity and access management
