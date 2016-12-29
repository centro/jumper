package net.centro.rtb.http;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import org.glassfish.grizzly.compression.zip.GZipEncoder;
import org.glassfish.jersey.client.filter.EncodingFeature;
import org.glassfish.jersey.media.multipart.*;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.glassfish.jersey.message.internal.EntityInputStream;
import org.glassfish.jersey.message.internal.ReaderWriter;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.rules.TemporaryFolder;
import javax.activation.MimetypesFileTypeMap;
import javax.imageio.ImageIO;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import static org.junit.Assert.*;
/**
 * Created by ofir.gal on 11/15/16.
 */
public class HttpConnectorTest extends JerseyTest {

    final static int SLOW = 200;

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Singleton
    @Provider
    @Path("/")
    public static class testResource {//implements ContainerRequestFilter {
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

            System.out.println("File ==> " + fileDetails);
            System.out.println(CharStreams.toString(new InputStreamReader(uploadedInputStream)));

            //return Response.status(201).build();
        }

//        @Override
//        public void filter(ContainerRequestContext containerRequestContext) throws IOException {
//            System.out.println("=> ");
//            System.out.println("=> " + containerRequestContext.getHeaders());
//
//        }
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
                //.register(GZipInterceptor.class)
                .register(MultiPartFeature.class)

    ;}

    @Test
    public void testGetResponseBody() throws Exception {
        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .setConnectorProvider(Http.ConnectorProvider.HttpUrlConnector)
                .url("http://localhost:9998/slow")
                .async()
                .build()
                .execute();
        System.out.println("==>" + httpConnector.geResponseTime() + "ms");
        httpConnector.execute();
        System.out.println("==>" + httpConnector.geResponseTime() + "ms");
        httpConnector.execute();
        System.out.println("==>" + httpConnector.geResponseTime() + "ms");
        httpConnector.execute();
        System.out.println("==>" + httpConnector.geResponseTime() + "ms");
        httpConnector.execute();
        assertTrue(httpConnector.getResponseBody(String.class).equals("Hello slow"));

        httpConnector = HttpConnectorBuilder.newBuilder()
                .setConnectorProvider(Http.ConnectorProvider.HttpUrlConnector)
                .url("http://localhost:9998/fast")
                .build()
                .execute();
        assertTrue(httpConnector.getResponseBody(String.class).equals("Hello fast"));

    }

    @Test
    public void testGetResponseBodyPost() throws Exception {
        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .setConnectorProvider(Http.ConnectorProvider.HttpUrlConnector)
                .url("http://localhost:9998/add")
                .setMethod(Http.HttpMethod.POST)
                .setBody("{\"a\":\"5\",\"b\":\"5\"}")
                .addHeaderProperty("Content-Type", "application/json")
                .build()
                .execute();
        Integer b = Integer.valueOf(httpConnector.getResponseBody());
        assertTrue(b.equals(10));
    }

    @Test
    public void testIvocationCallback() throws InterruptedException, IOException, URISyntaxException {

        InvocationCallback invocationCallback = new InvocationCallback<Response>() {
            @Override
            public void completed(Response response) {

                try {
                    HttpConnector.saveToFile(response, Paths.get("/Users/ofir.gal/test.txt"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                File test = new File ("/Users/ofir.gal/test.txt");
                Assert.assertTrue(test.exists());
            }

            @Override
            public void failed(Throwable throwable) {
                System.out.println("oops");
                fail();
            }
        };

        File tempFolder = testFolder.newFolder("download");
        tempFolder.deleteOnExit();
        String downloadFile = tempFolder.getAbsolutePath()+"/download.zip";

            HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                    .url("http://localhost:9998/slow")
                    .async(invocationCallback)
                    .saveToFile(downloadFile)
                    .build()
                    .execute();

        httpConnector.saveToFile();

        File test = new File (tempFolder.getAbsolutePath()+"/download.zip");

        Assert.assertTrue(test.exists());

        Thread.sleep(10000);
    }

    @Test
    public void getCookie() throws Exception {

        HttpConnector http = HttpConnectorBuilder.newBuilder().url("http://localhost:9998/cookie")
                .storeCookies()
                .build()
                .execute();

        Cookie cookie = HttpConnectorCookieManager.getCookie("test");

        Assert.assertEquals(cookie.getName(), "test");
        Assert.assertEquals(cookie.getValue(), "passed");
    }


    @Test
    public void testGetResponseCode() throws URISyntaxException {

        HttpConnector http = null;

        try {
            http = new HttpConnectorBuilder()
                    .url(Http.HttpProtocol.HTTP, "localhost", 9998, "/fast")
                    .setMethod(Http.HttpMethod.GET)
                    .async()
                    .build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        http.execute();

        System.out.print(http.getResponseCode() + " : ");
        System.out.println(http.getResponseMessage());

        Assert.assertEquals(http.getResponseCode(),200);
    }

    @Test
    public void testDownloadFileAsync() throws IOException {

        InvocationCallback invocationCallback = new InvocationCallback<Response>() {
            @Override
            public void completed(Response response) {
                try {
                    System.out.println("Downloading to temp file:");
                    System.out.println(testFolder.getRoot().getAbsolutePath()+File.separator +"download" + File.separator + "download.zip");
                    HttpConnector.saveToFile(response, Paths.get(testFolder.getRoot().getAbsolutePath() + File.separator + "download" + File.separator + "download.zip"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                File test = new File (testFolder.getRoot().getAbsolutePath()+"/download/download.zip");
                Assert.assertTrue(test.exists());
                try {
                    byte[] read = Files.readAllBytes(test.toPath());
                    byte[] compare = {123};
                    Assert.assertEquals(read[0],compare[0]);
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }

            @Override
            public void failed(Throwable throwable) {
                System.out.println("oops");
                fail();

            }
        };

        File tempFile = testFolder.newFile("test.zip");
        tempFile.deleteOnExit();
        File tempFolder = testFolder.newFolder("download");
        tempFolder.deleteOnExit();
        String downloadFile = tempFolder.getAbsolutePath()+ File.separator + "download.zip";

        byte[] data = {123};
        java.nio.file.Path file = Paths.get(tempFile.toURI());
        Files.write(file,data);

        HttpConnector http = null;
        java.nio.file.Path path = Paths.get(downloadFile);

        try {
            http = new HttpConnectorBuilder()
                    .url(("http://localhost:9998/file?path=" + tempFile.getAbsolutePath()).replace("\\","%2F"))
                    .setMethod(Http.HttpMethod.GET)
                    .saveToFile(path)
                    .async(invocationCallback)
                    .build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        http.execute();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    @Test
    public void testDownloadFile() throws IOException {


        File tempFile = testFolder.newFile("test.zip");
        tempFile.deleteOnExit();
        File tempFolder = testFolder.newFolder("download");
        tempFolder.deleteOnExit();
        String downloadFile = tempFolder.getAbsolutePath()+"/download.zip";

        byte data[] = {123};
        java.nio.file.Path file = Paths.get(tempFile.toURI());
        Files.write(file,data);

        HttpConnector http = null;
        java.nio.file.Path path = Paths.get(downloadFile);


        try {
            http = new HttpConnectorBuilder()
                    .url(("http://localhost:9998/file?path=" + tempFile.getAbsolutePath()).replace("\\","%2F"))
                    .setMethod(Http.HttpMethod.GET)
                    .saveToFile(path)
                    .build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        http.execute();


        http.saveToFile();

        File test = new File (tempFolder.getAbsolutePath()+"/download.zip");

        Assert.assertTrue(test.exists());

        byte[] read = Files.readAllBytes(test.toPath());

        assertEquals(data[0], read[0]);

    }
    @Test
    public void testSSL() throws URISyntaxException {

        HttpConnector b = HttpConnectorBuilder.newBuilder()
                .url("https://google.com")
                .trustAllSslContext()
                .build();
        b.execute();
        Assert.assertTrue(b.getResponseCode() == 200);
    }

    @Test
    public void simpleGet() throws URISyntaxException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("http://localhost:9998/fast")
                .build()
                .execute();
        Response response = httpConnector.getRawResponse();
        HttpConnector.getResponseBody(response, String.class);
        response.getStatusInfo();
    }

    @Test
    public void testMeasureResponseTimeSync() throws URISyntaxException, InterruptedException {
        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("http://localhost:9998/slow")
                .build();

        httpConnector.execute();

        Thread.sleep(SLOW+1000);

        assertTrue(httpConnector.getResponseCode() == 200);
        assertTrue(httpConnector.geResponseTime() > SLOW);
        Assert.assertTrue(httpConnector.geResponseTime() != -1);

    }

    @Test
    public void testMeasureResponseTimeAsync() throws URISyntaxException, InterruptedException {
        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("http://localhost:9998/slow")
                .async()
                .build();

        httpConnector.execute();

        Thread.sleep(SLOW+1000);

        assertTrue(httpConnector.getResponseCode() == 200);
        assertTrue(httpConnector.geResponseTime() > SLOW);
        Assert.assertTrue(httpConnector.geResponseTime() != -1);

    }

    @Test
    public void testMeasureResponseTimeAsyncCustomCallback() throws URISyntaxException, InterruptedException {

        InvocationCallback<Response> invocationCallback = new InvocationCallback<Response>() {
            @Override
            public void completed(Response response) {
                System.out.println("Custom callback: " + response.getStatus());
                Assert.assertTrue(response.hasEntity());
            }

            @Override
            public void failed(Throwable throwable) {
                fail();
            }
        };

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("http://localhost:9998/slow")
                .async(invocationCallback)
                .build();

        httpConnector.execute();

        Thread.sleep(SLOW+1500);

        assertTrue(httpConnector.getResponseCode() == 200);
        assertTrue(httpConnector.geResponseTime() > SLOW);
        Assert.assertTrue(httpConnector.geResponseTime() != -1);
    }

    @Test
    public void getAsJson() throws URISyntaxException {
        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("http://localhost:9998/json")
                .build();

        httpConnector.execute();

        ObjectNode objectNode = httpConnector.getResponseBody(ObjectNode.class);
        System.out.println("==>" + objectNode.get("key"));

    }



    @Test
    public void testJson() throws URISyntaxException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/get")
                .build();

        try {
            httpConnector.execute();
            System.out.println(httpConnector.getResponseBody(ObjectNode.class).get("headers").get("User-Agent"));
            ObjectNode objectNode = httpConnector.getResponseBody(ObjectNode.class);
            assertTrue(objectNode.has("headers"));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.fillInStackTrace());
        }


    }

    @Test
    public void testXML() throws URISyntaxException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/xml")
                .build();

        try {
            httpConnector.execute();
            System.out.println(httpConnector.getResponseBody(String.class));
            Assert.assertTrue(httpConnector.getResponseBody().contains("xml"));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.fillInStackTrace());
        }


    }

    @Test
    public void testJPG() throws URISyntaxException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/image/jpeg")
                .build();

        try {
            httpConnector.execute();
            BufferedImage image = null;
            try {
                image = ImageIO.read(httpConnector.getResponseBody(InputStream.class));
                System.out.println(image);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.fillInStackTrace());
        }
    }

    @Test
    public void testRedirect() throws URISyntaxException, IOException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/redirect-to?url=http%3A%2F%2Fexample.com%2F")
                .setMethod(Http.HttpMethod.GET)
                .follow_redirect(true)
                .setConnectorProvider(Http.ConnectorProvider.Apache)
                .setConnectTimeout(10000)
                .build();

        try {
            httpConnector.execute();
            Assert.assertTrue(httpConnector.getResponseBody().length() > 100);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.fillInStackTrace());
        }
    }

    @Test
    public void simpleGetHeaders() throws URISyntaxException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("http://localhost:9998/fast")
                .build()
                .execute();
        Response response = httpConnector.getRawResponse();
        response.getHeaders();
        HttpConnector.getResponseBody(response, String.class);
        response.getStatusInfo();
    }



    @Test
    public void sslAll() throws URISyntaxException {

        HttpConnector httpConnector = HttpConnectorBuilder.newBuilder()
                .url("https://httpbin.org/get")
                .trustAllSslContext()
                .build()
                .execute();
    }

}