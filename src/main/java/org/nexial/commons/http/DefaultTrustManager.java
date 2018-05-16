/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.commons.http;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultTrustManager, unlike default {@link X509TrustManager}, accepts self-signed
 * certificates.
 *
 * @author Mike Liu
 */
public class DefaultTrustManager implements X509TrustManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTrustManager.class);
    private X509TrustManager standardTrustManager;

    public DefaultTrustManager(KeyStore keystore) throws NoSuchAlgorithmException, KeyStoreException {
        super();
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keystore);

        TrustManager[] trustmanagers = factory.getTrustManagers();
        if (trustmanagers.length == 0) { throw new NoSuchAlgorithmException("no trust manager found"); }
        this.standardTrustManager = (X509TrustManager) trustmanagers[0];
    }

    /** @see X509TrustManager#checkClientTrusted(X509Certificate[], String authType) */
    public void checkClientTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        standardTrustManager.checkClientTrusted(certificates, authType);
    }

    /** @see X509TrustManager#checkServerTrusted(X509Certificate[], String authType) */
    public void checkServerTrusted(X509Certificate[] certificates, String authType) throws CertificateException {
        if (certificates != null && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Server certificate chain:");
            for (int i = 0; i < certificates.length; i++) {
                LOGGER.debug("X509Certificate[" + i + "]=" + certificates[i].getSubjectDN());
            }
        }

        if (certificates != null && certificates.length > 0) {
            // some env (e.g. staging) has more than 1 certificate per server.  Since we don't
            // know which one is the right (or "best") one, we'll need to try them until a
            // successful validation is reached.
            for (int i = 0; i < certificates.length; i++) {
                X509Certificate cert = certificates[i];
                try {
                    // keep trying until we get a good one
                    cert.checkValidity();
                    if (LOGGER.isInfoEnabled()) { LOGGER.info("validated certificate " + cert); }
                    return;
                } catch (CertificateExpiredException e) {
                    // don't fail-fast... complain and keep trying
                    if (i < certificates.length - 1) {
                        LOGGER.error("Unable to validate certificate " + cert +
                                     " due to " + e + ". Trying next certication...");
                    } else {
                        // last attempt... die
                        throw e;
                    }
                }
            }
        } else {
            standardTrustManager.checkServerTrusted(certificates, authType);
        }
    }

    /** @see X509TrustManager#getAcceptedIssuers() */
    public X509Certificate[] getAcceptedIssuers() { return this.standardTrustManager.getAcceptedIssuers(); }
}
