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

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.nexial.core.model.TestProject;
import org.nexial.core.utils.ConsoleUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

/**
 * Class to set up the configuration settings.
 */
class SetupBeanFactory implements BeanFactoryAware {
    private SetupBeanFactory() { }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        try {
            Class.forName("org.nexial.core.config.Setup");
        } catch (ClassNotFoundException e) {
            ConsoleUtils.log("No predefined nexial-setup found");
        } catch (Exception e) {
            ConsoleUtils.log("Exception is " + e.getMessage());
            if (e instanceof NullPointerException || StringUtils.equals(e.getMessage(), "null")) {
                ConsoleUtils.log("WARNING: NEXIAL MUST RUN UNDER JAVA 1.8.0_152 OR ABOVE. PLEASE CONSIDER DOWNLOAD " +
                                 "THE LATEST JAVA 1.8 FROM http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html");
            }
        }

        // preference of properties is given in the following order: -D parameters, config beans, properties in
        // the project.properties file and finally properties in the data file.
        Set<String> projectPropertyKeys = TestProject.listProjectPropertyKeys();
        if (!projectPropertyKeys.isEmpty()) {
            projectPropertyKeys.forEach(property -> {
                if (StringUtils.isNotBlank(property) && StringUtils.isBlank(System.getProperty(property))) {
                    // since we don't have this `prop` in system, we will use the one from project.properties
                    String value = TestProject.getProjectProperty(property);
                    if (StringUtils.isNotEmpty(value)) { System.setProperty(property, value); }
                }
            });
        }
    }
}
