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

package org.nexial.core.variable;

import java.util.*;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.set.ListOrderedSet;

public class OrderedKeyProperties extends Properties {
    private final ListOrderedSet<String> keys = new ListOrderedSet<>();

    public static class KeyEnumeration<E> implements Enumeration<E> {
        private final Iterator<E> iterator;

        public KeyEnumeration(Iterator<E> iterator) { this.iterator = iterator; }

        public E nextElement() { return iterator.next(); }

        public boolean hasMoreElements() { return iterator.hasNext(); }
    }

    @Override
    public synchronized Object setProperty(String key, String value) {
        keys.add(key);
        return super.setProperty(key, value);
    }

    @Override
    public synchronized Enumeration<Object> keys() { return new KeyEnumeration<>(keySet().iterator()); }

    @Override
    public synchronized Object put(Object key, Object value) {
        keys.add(Objects.toString(key));
        return super.put(key, value);
    }

    @Override
    public synchronized Object remove(Object key) {
        keys.remove(Objects.toString(key));
        return super.remove(key);
    }

    @Override
    public synchronized void putAll(Map<?, ?> t) {
        if (t != null) {
            t.forEach((key, value) -> keys.add(Objects.toString(key)));
            super.putAll(t);
        }
    }

    @Override
    public synchronized void clear() {
        keys.clear();
        super.clear();
    }

    @NotNull
    @Override
    public Set<Object> keySet() {
        Set<Object> objKeys = new ListOrderedSet<>();
        objKeys.addAll(keys);
        return objKeys;
    }

    @Override
    public synchronized boolean remove(Object key, Object value) {
        keys.remove(Objects.toString(key));
        return super.remove(key, value);
    }
}
