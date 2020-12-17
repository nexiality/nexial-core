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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.utils.ConsoleUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.nexial.core.NexialConst.FlowControls.FILTER_CHAINING_SEP;
import static org.nexial.core.NexialConst.TOKEN_END;
import static org.nexial.core.NexialConst.TOKEN_START;
import static org.nexial.core.model.NexialFilter.ITEM_SEP;
import static org.nexial.core.model.NexialFilterComparator.Any;

public class NexialFilterList extends ArrayList<NexialFilter> {

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

    public boolean isMatched(ExecutionContext context, String msgPrefix) {
        // * condition means ALWAYS MATCHED --> meaning always match
        if (containsAny()) {
            ConsoleUtils.log(msgPrefix + " found ANY - ALWAYS MATCH");
            return true;
        }

        for (NexialFilter filter : this) {
            // rework the control list now that we have the context
            List<String> controlList = filter.getControlList();
            if (CollectionUtils.size(controlList) == 1 &&
                StringUtils.containsAny(controlList.get(0), TOKEN_START, TOKEN_END)) {

                String[] controls = StringUtils.split(context.replaceTokens(controlList.get(0)), ITEM_SEP);
                controlList.clear();
                controlList.addAll(Arrays.asList(controls));
            }

            String subject = filter.getSubject();
            if (StringUtils.isNotBlank(subject) && !filter.isMatch(context, msgPrefix)) { return false; }
        }

        return true;
    }
}
