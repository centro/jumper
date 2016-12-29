package net.centro.rtb.http;

import com.google.common.io.CharStreams;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.*;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.activation.MimetypesFileTypeMap;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by ofir.gal on 12/27/16.
 */
public class HttpConnectorCompression extends JerseyTest {

    final static int SLOW = 200;

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Singleton
    @Provider
    @Path("/")
    public static class testResource { //implements ContainerRequestFilter {
        @GET
        @Path("slow")
        public Response getHelloSlow() {
            try {
                Thread.sleep(SLOW);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return Response.status(200).entity("Hello slow").build();
        }
        @GET
        @Path("fast")
        public String getHelloFast() {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "Hello fast";
        }

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        @Path("add")
        public Response add(Calc calc) {
            return Response.status(201).entity(calc.getA()+calc.getB()).build();
        }

        @GET
        @Path("file")
        @Produces("application/zip")
        public Response getFile(@QueryParam("path") String path) {

            File f = new File(path);
            String mt = new MimetypesFileTypeMap().getContentType(f);

            return Response.ok(f,mt).build();
        }

        @GET
        @Path("cookie")
        public Response cookie() {
            return Response.ok().cookie(new NewCookie("test", "passed")).build();
        }

        @GET
        @Path("json")
        @Produces(MediaType.APPLICATION_JSON)
        public Response json() {
            return Response.ok("{\"key\":\"value\"}").build();
        }

        @POST
        @Path("gzip")
        @Consumes("application/json")
        public Response gzip(Calc calc) {
            System.out.println("==> " + calc.getA());
            return Response.status(201).entity(calc.getA()+calc.getB()).build();
        }

        @POST
        @Path("multi")
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public void multi(@FormDataParam("test") InputStream uploadedInputStream,
                          @FormDataParam("test") FormDataContentDisposition fileDetails) throws IOException {

            System.out.println("test");
            System.out.println("File ==> " + fileDetails);
            System.out.println(CharStreams.toString(new InputStreamReader(uploadedInputStream)));

            //return Response.status(201).build();
        }

//        @Override
//        public void filter(ContainerRequestContext containerRequestContext) throws IOException {
//            System.out.println("=> " + containerRequestContext.toString());
//            System.out.println("=> " + containerRequestContext.getHeaders());
//
//        }
    }


    @Provider
    public static class GZipInterceptor implements ReaderInterceptor, WriterInterceptor {
        @Override
        public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
            System.out.println("=> " + context.getHeaders());
            List<String> header = context.getHeaders().get("Content-Encoding");
            // decompress gzip stream only
            if (header != null && header.contains("gzip"))
                System.out.println("decompressing GZIP");
            context.setInputStream(new GZIPInputStream(context.getInputStream()));
            System.out.println("decompressed");
            return context.proceed();
        }

        @Override
        public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
            System.out.println(context.getHeaders());
            context.setOutputStream(new GZIPOutputStream(context.getOutputStream()));
            context.getHeaders().add("Content-Encoding", "gzip");
            System.out.println(context.getHeaders());
            context.proceed();
        }
    }

    private static class Calc {
        int a,b;

        public Calc() {}
        public Calc(int a, int b) {
            this.a = a;
            this.b = b;
        }

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }

        public int getB() {
            return b;
        }

        public void setB(int b) {
            this.b = b;
        }
    }

    @Override
    protected Application configure() {
        return new ResourceConfig(testResource.class)
                .packages("org.glassfish.jersey.examples.jackson")
                .register(JacksonFeature.class)
                .register(GZipInterceptor.class)
                .register(MultiPartFeature.class)

                ;}


    @Test
    public void testGzipRequest() throws URISyntaxException, IOException {

        String body = ("{\"a\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\",\"b\":\"5\"}");

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("http://localhost:9998/gzip")
                .setMethod(Http.HttpMethod.POST)
                .addHeaderProperty("Content-Type", "application/json")
                .setBody(body)
                .compress(Http.Encoding.GZIP)
                .build();

        try {
            httpConnector.execute();
            System.out.println(httpConnector.getResponseBody());
            System.out.println(httpConnector.getResponseCode());
            System.out.println(httpConnector.getResponseMessage());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.fillInStackTrace());
        }
    }

    @Test
    public void testGzipDecompress() throws URISyntaxException, IOException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/gzip")
                .setMethod(Http.HttpMethod.GET)
                .build();
        try {
            httpConnector.execute();
            System.out.println(httpConnector.getResponseBody());
            System.out.println(httpConnector.getRawResponse().getHeaderString("Content-Encoding"));
            System.out.println(httpConnector.getResponseBody(InputStream.class));
            System.out.println(CharStreams.toString(new InputStreamReader(httpConnector.getResponseBody(InputStream.class))));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.fillInStackTrace());
        }
    }

    @Test
    public void testDeflate() throws URISyntaxException, IOException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/deflate")
                .setMethod(Http.HttpMethod.GET)
                .compress(Http.Encoding.DEFLATE)
                .build()
                .execute();

        try {
            httpConnector.execute();
            System.out.println(httpConnector.getRawResponse().getMediaType());
            System.out.println(httpConnector.getRawResponse().getHeaders());
            System.out.println(httpConnector.getRawResponse().getHeaderString("Content-Encoding"));
            System.out.println(CharStreams.toString(new InputStreamReader(httpConnector.getResponseBody(InputStream.class))));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.fillInStackTrace());
        }
    }

    @Test
    public void multiPart() throws URISyntaxException, IOException {

        File file = testFolder.newFile("upload.txt");

        java.nio.file.Files.write(Paths.get(file.toURI()), "{\"2\":\"3\"}".getBytes("utf-8"), StandardOpenOption.CREATE);

        final FileDataBodyPart filePart = new FileDataBodyPart("test", file);
        final FormDataMultiPart multiPart = (FormDataMultiPart) new FormDataMultiPart()
                .bodyPart(filePart);
        //.bodyPart(multiPart1);

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("http://localhost:9998/multi")
                .addHeaderProperty("Content-Type", "multipart/form-data")
                .setBody(multiPart)
                .setMethod(Http.HttpMethod.POST)
                .build();

        httpConnector.execute();

    }
}
