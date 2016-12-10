package net.centro.rtb.http;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.glassfish.grizzly.compression.zip.GZipDecoder;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.grizzly.connector.GrizzlyConnectorProvider;
import org.glassfish.jersey.jetty.connector.JettyConnectorProvider;
import org.glassfish.jersey.message.internal.MessagingBinders;
import org.glassfish.jersey.netty.connector.NettyConnectorProvider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.ext.Provider;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * The ClientFactory class is used by the HttpConnector to get a Client class.
 * Cliet objects are cached in the ClientFactory class and expire one minute after last access.
 */
public class ClientFactory {

    private static CacheLoader loader;
    private static LoadingCache<ClientIdentifier,Client> cache;

    static {

        loader = new CacheLoader<ClientIdentifier,Client>() {
            @Override
            public Client load(ClientIdentifier ci) throws Exception {
                return createNewClient(ci.builder);
            }
        };
        cache = CacheBuilder.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build(loader);
    }

    public static Client getClient(HttpConnectorBuilder builder) {

        return cache.getUnchecked(new ClientIdentifier(builder));

    }

    private static Client createNewClient(HttpConnectorBuilder builder) {

        ClientConfig config = new ClientConfig();
        builder.getClientProperties().entrySet().stream().forEach(entry -> config.property(entry.getKey(), entry.getValue()));

        switch (builder.getConnectorProvider()) {

            case Grizzly:
                config.connectorProvider(new GrizzlyConnectorProvider());
                break;
            case Apache:
                config.connectorProvider(new ApacheConnectorProvider());
                break;
            case Jetty:
                config.connectorProvider(new JettyConnectorProvider());
                break;
            case Netty:
                config.connectorProvider(new NettyConnectorProvider());
                break;
            case HttpUrlConnector:
                //config.connectorProvider(new HttpUrlConnectorProvider()); // Jersey default
                break;
            default:
                break;
        }

        switch (builder.getConnType()) {

            case HTTP:
                return ClientBuilder.newBuilder().withConfig(config).build();
            case HTTPS:
                if (builder.isTrustAllSslContext()) {
                    return ClientBuilder.newBuilder().withConfig(config).sslContext(HttpConnectorBuilder.getTrustAllSslContext()).build();
                } else {
                    return ClientBuilder.newBuilder().withConfig(config).build();
                }
            default:
                return ClientBuilder.newBuilder().withConfig(config).build();
        }

    }

    public static long getCacheSize() {
        return cache.size();
    }

    /**
     * Setting the cache object stale timeout. Default is set to one minute.
     * @param timeout set the time numeric value.
     * @param timeUnit enum setting of minutes/seconds
     */
    public static void setCacheStaleTimeout(int timeout, TimeUnit timeUnit) {
        cache = CacheBuilder.newBuilder().expireAfterAccess(timeout, timeUnit).build(loader);
    }

    private static class ClientIdentifier {

        private Http.ConnectorProvider connectorProvider;
        private Http.HttpProtocol httpProtocol;
        private Map<String, Object> properties;
        private boolean trustAllSSL;

        private HttpConnectorBuilder builder;

        ClientIdentifier(HttpConnectorBuilder builder) {

            //this.redirect = builder.isRedirect();
            this.connectorProvider = builder.getConnectorProvider();
            this.httpProtocol = builder.getConnType();
            //this.threadPool = builder.getAsyncThreadPoolSize();
            this.properties = builder.getClientProperties();
            this.trustAllSSL = builder.isTrustAllSslContext();
            this.builder = builder;

        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClientIdentifier)) return false;

            ClientIdentifier that = (ClientIdentifier) o;

            if (connectorProvider != that.connectorProvider) return false;
            if (!properties.equals(that.properties)) return false;
            if (trustAllSSL != that.trustAllSSL) return false;
            return httpProtocol == that.httpProtocol;

        }

        @Override
        public int hashCode() {
            int result = connectorProvider != null ? connectorProvider.hashCode() : 0;
            result = 31 * result + (httpProtocol != null ? httpProtocol.hashCode() : 0);
            result = 31 * result + (properties != null ? properties.hashCode() : 0);
            return result;
        }
    }
}

