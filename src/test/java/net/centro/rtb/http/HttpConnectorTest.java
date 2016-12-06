package net.centro.rtb.http;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.rules.TemporaryFolder;

import javax.activation.MimetypesFileTypeMap;
import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.core.*;

import java.io.File;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;
/**
 * Created by ofir.gal on 11/15/16.
 */
public class HttpConnectorTest extends JerseyTest {

    final static int SLOW = 200;

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Path("/")
    public static class testResource {
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
        int b = httpConnector.getResponseBody(Integer.class);
        assertTrue(httpConnector.getResponseBody(Integer.class).equals(10));
    }

    @Test
    public void testIvocationCallback() throws InterruptedException, IOException, URISyntaxException {

        InvocationCallback invocationCallback = new InvocationCallback<Response>() {
            @Override
            public void completed(Response response) {

                HttpConnector.saveToFile(response, Paths.get("/Users/ofir.gal/test.txt"));
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

        Thread.sleep(5000);
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
                    System.out.println(testFolder.getRoot().getAbsolutePath()+"/download/download.zip");
                    HttpConnector.saveToFile(response, Paths.get(testFolder.getRoot().getAbsolutePath()+"/download/download.zip"));
                } catch (Error e) {
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
        String downloadFile = tempFolder.getAbsolutePath()+"/download.zip";

        byte[] data = {123};
        java.nio.file.Path file = Paths.get(tempFile.toURI());
        Files.write(file,data);

        HttpConnector http = null;
        java.nio.file.Path path = Paths.get(downloadFile);

        try {
            http = new HttpConnectorBuilder()
                    .url("http://localhost:9998/file?path=" + tempFile.getAbsolutePath())
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
                    .url("http://localhost:9998/file?path=" + tempFile.getAbsolutePath())
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
        //System.out.println(b.getResponseBody(String.class));

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

        Thread.sleep(SLOW+1000);

        assertTrue(httpConnector.getResponseCode() == 200);
        assertTrue(httpConnector.geResponseTime() > SLOW);
        Assert.assertTrue(httpConnector.geResponseTime() != -1);
    }

}