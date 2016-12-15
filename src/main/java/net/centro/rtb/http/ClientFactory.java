package net.centro.rtb.http;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.glassfish.grizzly.compression.zip.GZipDecoder;
import org.glassfish.grizzly.compression.zip.GZipEncoder;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.filter.EncodingFeature;
import org.glassfish.jersey.client.filter.EncodingFilter;
import org.glassfish.jersey.grizzly.connector.GrizzlyConnectorProvider;
import org.glassfish.jersey.jetty.connector.JettyConnectorProvider;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.message.filtering.EntityFilteringFeature;
import org.glassfish.jersey.message.internal.MessagingBinders;
import org.glassfish.jersey.netty.connector.NettyConnectorProvider;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
        //******
                      //  config.register(MultiPartFeature.class);
        //******
        switch (builder.getCompressionEncoding()) {
            case GZIP:
                config.register(GZIPWriterInterceptor.class);
                break;
            case DEFLATE:
                config.register(DeflateWriterInterceptor.class);
                break;
            default:
                break;
        }

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
        private Http.Encoding encoding;

        private HttpConnectorBuilder builder;

        ClientIdentifier(HttpConnectorBuilder builder) {

            //this.redirect = builder.isRedirect();
            this.encoding = builder.getCompressionEncoding();
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
            if (!encoding.equals(that.encoding)) return false;
            return httpProtocol == that.httpProtocol;

        }

        @Override
        public int hashCode() {
            int result = connectorProvider != null ? connectorProvider.hashCode() : 0;
            result = 31 * result + (httpProtocol != null ? httpProtocol.hashCode() : 0);
            result = 31 * result + (properties != null ? properties.hashCode() : 0);
            result = 31 * result + (encoding != null ? properties.hashCode() : 0);
            return result;
        }
    }

    @Provider
    public static class GZIPWriterInterceptor implements WriterInterceptor {

        @Override
        public void aroundWriteTo(WriterInterceptorContext context)
                throws IOException, WebApplicationException {

            MultivaluedMap<String,Object> headers = context.getHeaders();
            headers.add("Content-Encoding", "gzip");

            final OutputStream outputStream = context.getOutputStream();
            context.setOutputStream(new GZIPOutputStream(outputStream));
            context.proceed();
        }

    }

    @Provider
    public static class DeflateWriterInterceptor implements WriterInterceptor {

        @Override
        public void aroundWriteTo(WriterInterceptorContext context)
                throws IOException, WebApplicationException {

            MultivaluedMap<String,Object> headers = context.getHeaders();
            headers.add("Content-Encoding", "deflate");

            final OutputStream outputStream = context.getOutputStream();
            context.setOutputStream(new DeflaterOutputStream(outputStream));
            context.proceed();
        }

    }
}

