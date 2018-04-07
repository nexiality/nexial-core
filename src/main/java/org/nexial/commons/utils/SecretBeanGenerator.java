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

package org.nexial.commons.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.NexialConst;
import org.nexial.core.model.TestProject;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import static java.io.File.separator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.join;
import static org.nexial.core.NexialConst.OPT_PROJECT_BASE;
import static org.nexial.core.NexialConst.SECRET_FILE;

/**
 * Class to generate secret beans.
 */
public class SecretBeanGenerator implements BeanFactoryAware {
    private static final SecretBeanGenerator secretClass = new SecretBeanGenerator();

    private SecretBeanGenerator() { }

    public static SecretBeanGenerator getSecretClass() { return secretClass; }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        try {
            final String projectHome = System.getProperty(OPT_PROJECT_BASE);
            final String secretLocation =
                projectHome != null ?
                EncryptionUtility.getSecretLocation(new File(projectHome).getAbsolutePath()) :
                EMPTY;

            String fileContent;
            if (isNotEmpty(secretLocation)) {
                Path path = Paths.get(join(secretLocation, separator, SECRET_FILE));
                if (path.toFile().exists()) {
                    fileContent = new String(Files.readAllBytes(path));
                } else {
                    fileContent = getDefaultFileContent();
                }
            } else {
                fileContent = getDefaultFileContent();
            }

            if (StringUtils.isNotBlank(fileContent)) {
                final Map<String, String> secrets = EncryptionUtility.retrieveEncryptedSecrets(fileContent);

                // preference of properties is given in the following order: -D parameters, secret beans, properties in
                // the project.properties file and finally properties in the data file.

                Set<String> keys = secrets.keySet();
                if (!keys.isEmpty()) {
                    final ConfigurableBeanFactory configurableBeanFactory = (ConfigurableBeanFactory) beanFactory;
                    keys.forEach(property -> {
                        configurableBeanFactory.registerSingleton(property, secrets.get(property));
                        if (StringUtils.isBlank(System.getProperty(property))) {
                            System.setProperty(property, secrets.get(property));
                        }
                    });
                }
            }

            Set<String> projectPropertyKeys = TestProject.listProjectPropertyKeys();
            if (!projectPropertyKeys.isEmpty()) {
                projectPropertyKeys.forEach(property -> {
                    if (StringUtils.isBlank(System.getProperty(property))) {
                        System.setProperty(property, TestProject.getProjectProperty(property));
                    }
                });
            }
        } catch (IOException | DecoderException | GeneralSecurityException e) {
            throw new FatalBeanException(e.getMessage());
        }
    }

    /**
     * Retrieves the file content of the file stored in the {@link NexialConst#SECRET_FILE}.
     *
     * @return content of the file {@link NexialConst#SECRET_FILE}.
     */
    private String getDefaultFileContent() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(SECRET_FILE);
        if (inputStream == null) { return null; }
        return IOUtils.toString(inputStream, UTF_8);
    }
}
