/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.plugins.ws;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class NaiveConnectionSocketFactory extends SSLConnectionSocketFactory {
    static final SSLContext I_TRUST_EVERYONE = initNaiveContext();
    static final HostnameVerifier NOOP_HOST_VERIFIER = initNoopHostVerifier();

    public NaiveConnectionSocketFactory() { super(I_TRUST_EVERYONE, NOOP_HOST_VERIFIER); }

    private static HostnameVerifier initNoopHostVerifier() { return new NoopHostnameVerifier(); }

    private static SSLContext initNaiveContext() {
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");

            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) { }

                public void checkServerTrusted(X509Certificate[] chain, String authType) { }

                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };

            sslContext.init(null, new TrustManager[]{tm}, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Unable to initialize NaiveConnectionSocketFactory: " + e.getMessage());
        }

        return sslContext;
    }
}