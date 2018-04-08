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
 */

package org.nexial.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;

import static org.nexial.core.model.NexialFilterComparator.Any;

public class NexialFilterList extends ArrayList<NexialFilter> {
    public static final String FILTER_CHAINING_SEP = " & ";

    private String filterText;
    private boolean containsAny;

    public NexialFilterList() { super(); }

    public NexialFilterList(String filterText) {
        // sanity check
        this.filterText = TextUtils.removeExcessWhitespaces(filterText);
        if (StringUtils.isBlank(this.filterText)) { return; }

        // parse and store
        String[] filters = StringUtils.splitByWholeSeparator(this.filterText, FILTER_CHAINING_SEP);
        if (ArrayUtils.isEmpty(filters)) {
            add(NexialFilter.newInstance(filterText));
        } else {
            Arrays.stream(filters).forEach(filter -> add(NexialFilter.newInstance(filter)));
        }
    }

    public String getFilterText() { return filterText; }

    public boolean containsAny() { return containsAny; }

    @Override
    public boolean add(NexialFilter filter) {
        if (filter != null && filter.getComparator() == Any) { containsAny = true; }
        return super.add(filter);
    }

    @Override
    public void add(int index, NexialFilter filter) {
        if (filter != null && filter.getComparator() == Any) { containsAny = true; }
        super.add(index, filter);
    }

    @Override
    public boolean addAll(Collection<? extends NexialFilter> c) {
        c.forEach(filter -> { if (filter != null && filter.getComparator() == Any) { containsAny = true; }});
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends NexialFilter> c) {
        c.forEach(filter -> { if (filter != null && filter.getComparator() == Any) { containsAny = true; }});
        return super.addAll(index, c);
    }
}
