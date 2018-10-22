package org.grobid.core.utilities;

import java.io.*;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;

import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Downloader {

    public File download(URL url, File dstFile) {

        CloseableHttpClient httpclient = HttpClients.custom()
                .setUserAgent("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:60.0) Gecko/20100101 Firefox/60.0")
                .setDefaultRequestConfig(RequestConfig.custom().setCookieSpec(CookieSpecs.STANDARD).setCircularRedirectsAllowed(true).build())
                .setRedirectStrategy(new LaxRedirectStrategy()) // adds HTTP REDIRECT support to GET and POST methods 
                .build();
        //httpclient.getParams().setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true); // allow redirects to the same location
        try {
            HttpGet get = new HttpGet(url.toURI()); // we're using GET but it could be via POST as well
            File downloaded = httpclient.execute(get, new FileDownloadResponseHandler(dstFile));
            return downloaded;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            IOUtils.closeQuietly(httpclient);
        }
    }
    
    static class FileDownloadResponseHandler implements ResponseHandler<File> {

        private final File target;

        public FileDownloadResponseHandler(File target) {
            this.target = target;
        }

        @Override
        public File handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
            InputStream source = response.getEntity().getContent();
            FileUtils.copyInputStreamToFile(source, this.target);
            return this.target;
        }
        
    }
    
    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;
     
        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }
     
        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
              .forEach(consumer);
        }
    }

    /** 
     *  Normally no need to use this, the Apache http client is very robust 
     */
    public File downloadExternal(URL url, File dstFile) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

        if (isWindows) {
            throw new Exception("Windows does not support this method, use standard Apache Java Http Client");
        } 

        ProcessBuilder builder = new ProcessBuilder();
        builder.command("wget", "--user-agent=\"Mozilla/5.0 (Windows NT 5.2; rv:2.0.1) Gecko/20100101 Firefox/4.0.1\"", 
            "-O", dstFile.getPath(), url.toString());
        //System.out.println("wget --user-agent=\"Mozilla/5.0 (Windows NT 5.2; rv:2.0.1) Gecko/20100101 Firefox/4.0.1\" -O " 
        //    + dstFile.getPath() + " " + url.toString());
        Process process = builder.start();
        StreamGobbler streamGobbler = 
          new StreamGobbler(process.getInputStream(), System.out::println);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new Exception("External download failed, use standard Apache Java Http Client");
        }

        return dstFile;
    }
}