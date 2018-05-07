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

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.TestProject;
import org.nexial.core.utils.ConsoleUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

import java.util.Set;

/**
 * Class to set up the configuration settings.
 */
class SecretBeanGenerator implements BeanFactoryAware {
    private SecretBeanGenerator() {
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        try {
            Class.forName("org.nexial.config.Setup");
        } catch (ClassNotFoundException e) {
            ConsoleUtils.log("No pre defined configurations set.");
        } catch (Exception e) {
            ConsoleUtils.log("Exception is " + e.getMessage());
        }

        // preference of properties is given in the following order: -D parameters, config beans, properties in
        // the project.properties file and finally properties in the data file.
        Set<String> projectPropertyKeys = TestProject.listProjectPropertyKeys();
        if (!projectPropertyKeys.isEmpty()) {
            projectPropertyKeys.forEach(property -> {
                if (StringUtils.isBlank(System.getProperty(property))) {
                    System.setProperty(property, TestProject.getProjectProperty(property));
                }
            });
        }
    }
}
