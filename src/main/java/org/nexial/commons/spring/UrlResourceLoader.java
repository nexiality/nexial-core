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

package org.nexial.commons.spring;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import javax.naming.NamingException;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.AbstractResource;
import org.springframework.jndi.JndiTemplate;

/**
 * URL resources can be loaded (and overload) Spring context properties
 *
 * @author Mike Liu
 */
public class UrlResourceLoader extends AbstractResource implements InitializingBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlResourceLoader.class);
    private Map<String, String> propToUrlMapping;
    private JndiTemplate jndi;
    private String harvestedUrls;

    public void setJndi(JndiTemplate jndi) { this.jndi = jndi; }

    public void setPropToUrlMapping(Map<String, String> propToUrlMapping) { this.propToUrlMapping = propToUrlMapping; }

    public String getDescription() {
        return this.getClass().getSimpleName() + " for these properties: " +
               ArrayUtils.toString(propToUrlMapping.keySet().toArray());
    }

    @Override
    public String getFilename() throws IllegalStateException { return "NONE"; }

    public void afterPropertiesSet() {
        StringBuilder buffer = new StringBuilder();
        for (String prop : propToUrlMapping.keySet()) {
            Object url = null;
            try {
                url = jndi.lookup(propToUrlMapping.get(prop));
            } catch (NamingException e) {
                // maybe we need to add java:comp/env
                try {
                    url = jndi.lookup("java:comp/env/" + propToUrlMapping.get(prop));
                } catch (NamingException e1) {
                    LOGGER.warn("URL lookup for '" + prop + "' resulted in exception: " + e + ". Ignored...");
                }
            }

            if (url != null) {
                buffer.append(prop).append("=").append(url.toString()).append("\n");
            } else {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("URL lookup for '" + prop + "'=null. Ignored...");
                }
            }
        }
        harvestedUrls = buffer.toString();
    }

    public InputStream getInputStream() {
        if (LOGGER.isDebugEnabled()) { LOGGER.debug("harvested URLs: " + harvestedUrls); }
        byte[] properties = harvestedUrls.getBytes();
        return new ByteArrayInputStream(properties);
    }
}
