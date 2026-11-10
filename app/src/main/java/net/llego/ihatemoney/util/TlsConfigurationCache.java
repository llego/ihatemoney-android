package net.llego.ihatemoney.util;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiFunction;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Reuse TLS configuration identities so Android can pool verified HTTPS connections. */
final class TlsConfigurationCache {
    static final class Configuration {
        final SSLSocketFactory socketFactory;
        final HostnameVerifier hostnameVerifier;

        Configuration(SSLSocketFactory socketFactory, HostnameVerifier hostnameVerifier) {
            this.socketFactory = socketFactory;
            this.hostnameVerifier = hostnameVerifier;
        }
    }

    private final BiFunction<X509TrustManager, HostnameVerifier, HostnameVerifier> verifierFactory;
    private X509TrustManager cachedTrustManager;
    private HostnameVerifier cachedDefaultVerifier;
    private Configuration cachedConfiguration;

    TlsConfigurationCache(BiFunction<X509TrustManager, HostnameVerifier, HostnameVerifier> verifierFactory) {
        this.verifierFactory = verifierFactory;
    }

    synchronized Configuration get(X509TrustManager manager, HostnameVerifier defaultVerifier)
            throws NoSuchAlgorithmException, KeyManagementException {
        if (cachedConfiguration == null || cachedTrustManager != manager || cachedDefaultVerifier != defaultVerifier) {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{manager}, null);
            Configuration configuration = new Configuration(context.getSocketFactory(),
                    verifierFactory.apply(manager, defaultVerifier));
            cachedTrustManager = manager;
            cachedDefaultVerifier = defaultVerifier;
            cachedConfiguration = configuration;
        }
        return cachedConfiguration;
    }

    synchronized void clear() {
        cachedTrustManager = null;
        cachedDefaultVerifier = null;
        cachedConfiguration = null;
    }
}
