/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.web;

import java.io.Closeable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;

/**
 * Client to test REST and Web Requests
 * 
 * @author Terry Packer
 *
 */
public class MangoHttpClient implements Closeable {

    private final String host;
    private final int port;
    private final boolean ssl;
    private final CloseableHttpClient client;
    private final HttpClientContext context;
    
    /**
     * 
     * @param host
     * @param port
     * @param ssl
     */
    public MangoHttpClient(String host, int port, boolean ssl) {
        this.host = host;
        this.port = port;
        this.ssl = ssl;

        this.client = HttpClientBuilder.create().build();
        this.context = HttpClientContext.create();
        this.context.setCookieStore(new BasicCookieStore());
        BasicClientCookie xsrfCookie = new BasicClientCookie("XSRF-TOKEN", UUID.randomUUID().toString());
        xsrfCookie.setDomain(host);
        xsrfCookie.setPath("/");
        this.context.getCookieStore().addCookie(xsrfCookie);
    }
    
    /**
     * 
     * @param username
     * @param password
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public CloseableHttpResponse login(String username, String password) throws ClientProtocolException, IOException, URISyntaxException {
        URIBuilder b = new URIBuilder();
        b = b.setHost(host).setPort(port).setScheme(ssl ? "https" : "http").setPath("/rest/v2/login");
        HttpPost request = new HttpPost(b.build());
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-Type", "application/json");
        
        for(Cookie cookie : this.context.getCookieStore().getCookies()) {
            if(cookie.getName().equals("XSRF-TOKEN")) {
                request.setHeader("X-XSRF-TOKEN", cookie.getValue());
                break;
            }
        }
        StringEntity entity = new StringEntity("{\"username\":\"admin\",\"password\":\"admin\"}");
        request.setEntity(entity);
        CloseableHttpResponse response = this.client.execute(request, context);
        return response;
    }
    
    /**
     * 
     * @param uri
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public CloseableHttpResponse get(String uri) throws ClientProtocolException, IOException, URISyntaxException {
        URIBuilder b = new URIBuilder();
        b = b.setHost(host).setPort(port).setScheme(ssl ? "https" : "http").setPath(uri);
        HttpGet request = new HttpGet(b.build());
        CloseableHttpResponse response = this.client.execute(request, context);
        return response;
    }

    /**
     * 
     * @param uri
     * @param body
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public CloseableHttpResponse post(String uri, HttpEntity body) throws ClientProtocolException, IOException, URISyntaxException {
        return post(uri, body, new NameValuePair[0]);
    }
    
    /**
     * Post 
     * @param uri
     * @param body
     * @param nvps
     * @return
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    public CloseableHttpResponse post(String uri, HttpEntity body, NameValuePair... nvps) throws ClientProtocolException, IOException, URISyntaxException {
        URIBuilder b = new URIBuilder();
        b = b.setHost(host).setPort(port).setScheme(ssl ? "https" : "http").setPath(uri);
        if(nvps != null) {
            b.addParameters(Arrays.asList(nvps));
        }
        
        HttpPost request = new HttpPost(b.build());
        request.setEntity(body);
        
        for(Cookie cookie : this.context.getCookieStore().getCookies()) {
            if(cookie.getName().equals("XSRF-TOKEN")) {
                request.setHeader("X-XSRF-TOKEN", cookie.getValue());
                break;
            }
        }
        CloseableHttpResponse response = this.client.execute(request, context);
        return response;
    }
    
    @Override
    public void close() throws IOException {
        this.client.close();
    }
    
}