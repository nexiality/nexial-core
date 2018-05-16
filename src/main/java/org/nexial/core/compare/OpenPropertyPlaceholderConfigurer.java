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

package org.nexial.core.compare;

import java.io.IOException;
import java.util.*;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.utils.ConsoleUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.util.CollectionUtils;

/**
 * $
 */
public class OpenPropertyPlaceholderConfigurer extends PropertyPlaceholderConfigurer {
    protected Map<String, Object> mergedProps = new LinkedHashMap<>();

    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            Properties props = mergeProperties();
            // Convert the merged properties, if necessary.
            convertProperties(props);

            // Let the subclass process the properties.
            processProperties(beanFactory, props);

            CollectionUtils.mergePropertiesIntoMap(props, mergedProps);
            mergeProperties(props, true);
        } catch (IOException ex) {
            throw new BeanInitializationException("Could not load properties", ex);
        }
    }

    public String deleteProperty(String name) { return Objects.toString(mergedProps.remove(name)); }

    public void setProperty(String name, String value) { mergedProps.put(name, mergeProperty(value)); }

    public void setMap(String name, Map<String, String> value) { mergedProps.put(name, value); }

    public boolean hasProp(String name) { return getProperty(name) != null; }

    public void setList(String name, List<Map<String, String>> value) { mergedProps.put(name, value); }

    public void setObject(String name, Boolean value) { mergedProps.put(name, value); }

    public void setObject(String name, Object value) { mergedProps.put(name, value); }

    public String getProperty(String name) { return MapUtils.getString(mergedProps, name); }

    public boolean getBooleanProperty(String name) { return Boolean.parseBoolean(getProperty(name)); }

    public Map<String, String> getMap(String name) {
        if (mergedProps.containsKey(name)) {
            Object value = mergedProps.get(name);
            if (value == null) { return null; }
            if (value instanceof Map) { return (Map<String, String>) value; }
        }
        return null;
    }

    public List<Map<String, String>> getList(String name) {
        if (mergedProps.containsKey(name)) {
            Object value = mergedProps.get(name);
            if (value == null) { return null; }
            // a little riskay..
            if (value instanceof List) { return (List<Map<String, String>>) value; }
        }
        return null;
    }

    public Object getObject(String name) { return mergedProps.get(name); }

    public void mergeProperties(Properties props, boolean overwrite) {
        if (MapUtils.isEmpty(props)) { return; }

        for (Enumeration en = props.propertyNames(); en.hasMoreElements(); ) {
            String key = (String) en.nextElement();
            String value = mergeProperty(props.getProperty(key));
            if (!overwrite && props.contains(key)) { continue; }
            ConsoleUtils.log("merging/overwrite property: " + key + "=" + value);
            mergedProps.put(key, value);
        }
    }

    public Map<String, String> getPropertyByPrefix(String prefix) {
        Map<String, String> props = new LinkedHashMap<>();
        mergedProps.keySet().stream().filter(key -> StringUtils.startsWith(key, prefix)).forEach(key -> {
            props.put(StringUtils.substringAfter(key, prefix), MapUtils.getString(mergedProps, key));
        });
        return props;
    }

    private String mergeProperty(String value) {
        int startIdx = StringUtils.indexOf(value, placeholderPrefix);
        int endIdx = StringUtils.indexOf(value, placeholderSuffix, startIdx + 2);
        while (startIdx != -1 && endIdx != -1) {
            String var = StringUtils.substring(value, startIdx + 2, endIdx);
            String searchFor = placeholderPrefix + var + placeholderSuffix;
            String replacedBy = MapUtils.getString(mergedProps, var, searchFor);
            if (!StringUtils.equals(searchFor, replacedBy)) {
                value = StringUtils.replace(value, searchFor, replacedBy);
                startIdx = StringUtils.indexOf(value, placeholderPrefix);
            } else {
                // skip the unreplaceable token
                startIdx = StringUtils.indexOf(value, placeholderPrefix, startIdx + 2);
                if (startIdx == -1) { break; }
            }

            endIdx = StringUtils.indexOf(value, placeholderSuffix, startIdx + 2);
        }
        return value;
    }
}