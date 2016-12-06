package net.centro.rtb.http;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.concurrent.Callable;
import com.codahale.metrics.*;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.server.ResourceConfig;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;


/**
 * This class tests the http connector using multiple threads and the utilization of the client cache
 */
public class HttpConnectorConcurrencyTest extends JerseyTest{

    public static final MetricRegistry metrics = new MetricRegistry();
    static com.codahale.metrics.Meter requests = metrics.meter("Server response");
    final Timer timer = metrics.timer(MetricRegistry.name("Client requests"));

    @Path("/load")
    public static class loadResource {
        @GET
        public String getHello() {
            requests.mark();
            try {
                Thread.sleep(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "Hello World!";
        }
    }

    @Override
    protected Application configure() {
        return new ResourceConfig(loadResource.class);
    }

    public class LoadTest implements Callable<String> {

        @Override
        public String call() {
            HttpConnector http = null;
            try {
                http = new HttpConnectorBuilder()
                        .url(Http.HttpProtocol.HTTP, "127.0.0.1", 9998, "/load")//.url("http://virt54.sitescout.com:80/advertisers/61297")//.url("http://jsonplaceholder.typicode.com:80/posts/1")
                        .setMethod(Http.HttpMethod.GET)
                        .async()
                        .setConnectorProvider(Http.ConnectorProvider.HttpUrlConnector)
                        .setAsyncThreadPoolSize(50)
                        .build();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            Timer.Context context = timer.time();
            http.execute();
            String s = http.getResponseBody(String.class);
            context.stop();

            return s;
        }
    }

    @Test
    public void loadTest1() throws Exception {

        startMetering();

        InvocationCallback invocationCallback = new InvocationCallback<Response>() {

            @Override
            public void completed(Response response) {
                response.close();
            }

            @Override
            public void failed(Throwable throwable) {

            }
        };

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url(Http.HttpProtocol.HTTP, "127.0.0.1", 9998, "/load")
                .setAsyncThreadPoolSize(10)
                .async(invocationCallback)
                .build();

        Timer.Context context;
        for (int i = 0; i < 15000; i++) {
            context = timer.time();
            httpConnector.execute();
            context.stop();
        }
        Thread.sleep(6000);
    }

    @Test
    public void loadTest2() throws Exception {
        startMetering();
        ExecutorService executor = Executors.newFixedThreadPool(50);

        List<Future<String>> list = new ArrayList<Future<String>>();
        for (int i = 0; i < 50000; i++) {
            Callable<String> worker = new LoadTest();
            Future<String> submit = executor.submit(worker);
            list.add(submit);
        }
        //long sum = 0;
        //Thread.sleep(2000);

        System.out.println(list.size());
        // now retrieve the result


        for (Future<String> future : list) {
            try {
                //System.out.println(future.get());
                future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        //System.out.println(sum);
        executor.shutdown();
    }


    private static void startMetering() {

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.MICROSECONDS)
                .convertDurationsTo(TimeUnit.MICROSECONDS)
                .build();
        reporter.start(2000, TimeUnit.MILLISECONDS);

        if (metrics.getGauges().size() == 0) {
            metrics.register(MetricRegistry.name("Clients in cache"),
                    new Gauge<Long>() {
                        @Override
                        public Long getValue() {
                            return ClientFactory.getCacheSize();
                        }
                    });
        }
    }

}
