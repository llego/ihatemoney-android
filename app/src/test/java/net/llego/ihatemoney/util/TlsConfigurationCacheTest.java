package net.llego.ihatemoney.util;

import org.junit.Test;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.X509TrustManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class TlsConfigurationCacheTest {
    private final HostnameVerifier verifier = (host, session) -> false;
    private final AtomicInteger wrappersCreated = new AtomicInteger();
    private final TlsConfigurationCache cache = new TlsConfigurationCache((manager, delegate) -> {
        wrappersCreated.incrementAndGet();
        return (host, session) -> delegate.verify(host, session);
    });

    @Test
    public void keepsSocketFactoryAndVerifierIdentitiesForConnectionPooling() throws Exception {
        X509TrustManager manager = rejectingManager();
        TlsConfigurationCache.Configuration first = cache.get(manager, verifier);
        TlsConfigurationCache.Configuration second = cache.get(manager, verifier);
        assertSame(first.socketFactory, second.socketFactory);
        assertSame(first.hostnameVerifier, second.hostnameVerifier);
        assertEquals(1, wrappersCreated.get());
        assertFalse(second.hostnameVerifier.verify("example.invalid", null));
    }

    @Test
    public void differentTrustManagerDoesNotShareTlsSessions() throws Exception {
        TlsConfigurationCache.Configuration first = cache.get(rejectingManager(), verifier);
        TlsConfigurationCache.Configuration second = cache.get(rejectingManager(), verifier);
        assertNotSame(first.socketFactory, second.socketFactory);
        assertNotSame(first.hostnameVerifier, second.hostnameVerifier);
    }

    @Test
    public void changingDefaultVerifierInvalidatesConfiguration() throws Exception {
        X509TrustManager manager = rejectingManager();
        TlsConfigurationCache.Configuration first = cache.get(manager, verifier);
        TlsConfigurationCache.Configuration second = cache.get(manager, (host, session) -> false);
        assertNotSame(first.socketFactory, second.socketFactory);
        assertNotSame(first.hostnameVerifier, second.hostnameVerifier);
    }

    @Test
    public void clearingCertificateDecisionsAlsoDiscardsReusableTlsState() throws Exception {
        X509TrustManager manager = rejectingManager();
        TlsConfigurationCache.Configuration first = cache.get(manager, verifier);
        cache.clear();
        TlsConfigurationCache.Configuration second = cache.get(manager, verifier);
        assertNotSame(first.socketFactory, second.socketFactory);
        assertNotSame(first.hostnameVerifier, second.hostnameVerifier);
    }

    private X509TrustManager rejectingManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("Not trusted");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("Not trusted");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
