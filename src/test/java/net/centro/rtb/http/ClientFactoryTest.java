package net.centro.rtb.http;

import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.client.Client;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Created by ofir.gal on 11/23/16.
 */
public class ClientFactoryTest {

    @Before
    public void purgeCache() {
        ClientFactory.setCacheStaleTimeout(1, TimeUnit.MINUTES);
    }
    @Test
    public void testGetUncachedClient() throws Exception {

        long i = ClientFactory.getCacheSize();
        HttpConnectorBuilder builder = HttpConnectorBuilder.newBuilder()
                .url("http://localhost");

        Client c = ClientFactory.getClient(builder);
        assertEquals(i + 1, ClientFactory.getCacheSize());

        builder = HttpConnectorBuilder.newBuilder()
                .url("http://localhost")
                .trustAllSslContext();
        c = ClientFactory.getClient(builder);
        assertEquals(i + 2, ClientFactory.getCacheSize());
    }

    @Test
    public void testCachedSSLClient() throws Exception {

        long i = ClientFactory.getCacheSize();
        HttpConnectorBuilder builder = HttpConnectorBuilder.newBuilder()
                .url("http://localhost");

        Client c = ClientFactory.getClient(builder);
        assertEquals(i + 1, ClientFactory.getCacheSize());

        builder = HttpConnectorBuilder.newBuilder()
                .url("http://localhost");
        c = ClientFactory.getClient(builder);
        assertEquals(i + 1, ClientFactory.getCacheSize());
    }

    @Test
    public void testUnCachedHttpProviderClient() throws Exception {

        long i = ClientFactory.getCacheSize();
        HttpConnectorBuilder builder = HttpConnectorBuilder.newBuilder()
                .url("http://localhost")
                .setConnectorProvider(Http.ConnectorProvider.Apache);

        Client c = ClientFactory.getClient(builder);
        assertEquals(i + 1, ClientFactory.getCacheSize());

        builder = HttpConnectorBuilder.newBuilder()
                .url("http://localhost");
        c = ClientFactory.getClient(builder);
        assertEquals(i + 2, ClientFactory.getCacheSize());
    }

    @Test
    public void testCachePurge() {
        long i = 0;
        ClientFactory.setCacheStaleTimeout(1, TimeUnit.SECONDS);
        HttpConnectorBuilder builder = null;
        try {
            builder = HttpConnectorBuilder.newBuilder()
                    .url("http://localhost")
                    .setConnectorProvider(Http.ConnectorProvider.Apache);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Client c = ClientFactory.getClient(builder);
        assertEquals(1, ClientFactory.getCacheSize());
        c = ClientFactory.getClient(builder);
        assertEquals(1, ClientFactory.getCacheSize());

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            builder = HttpConnectorBuilder.newBuilder()
                    .url("http://localhost");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        c = ClientFactory.getClient(builder);
        assertEquals(2, ClientFactory.getCacheSize());


    }

}