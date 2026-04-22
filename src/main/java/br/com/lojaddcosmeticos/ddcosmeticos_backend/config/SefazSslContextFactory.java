package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;

/**
 * Custom ProtocolSocketFactory to force Apache Commons HTTP Client
 * to use TLSv1.2 and bypass strict certificate chain validation
 * which often fails on Java 21 when connecting to SEFAZ.
 */
public class SefazSslContextFactory implements ProtocolSocketFactory {

    private SSLContext sslcontext = null;

    private SSLContext createSSLContext() {
        try {
            // Force TLSv1.2
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            // Use a TrustManager that accepts all certificates
            context.init(null, new TrustManager[]{new TrustAnyTrustManager()}, new java.security.SecureRandom());
            return context;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SSLContext getSSLContext() {
        if (this.sslcontext == null) {
            this.sslcontext = createSSLContext();
        }
        return this.sslcontext;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
        return getSSLContext().getSocketFactory().createSocket(host, port, clientHost, clientPort);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort, HttpConnectionParams params) throws IOException {
        // params are ignored in this implementation, but required by the interface
        return getSSLContext().getSocketFactory().createSocket(host, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return getSSLContext().getSocketFactory().createSocket(host, port);
    }

    // Trust manager that blindly accepts all certificates
    private static class TrustAnyTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
    }
}