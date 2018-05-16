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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.fileupload.disk.DiskFileItem;

/**
 * @author Original : plosson on 05-janv.-2006 10:46:33 - Last modified  by $Author$
 */
public class MonitoredDiskFileItem extends DiskFileItem {
    private transient MonitoredOutputStream mos = null;
    private transient OutputStreamListener listener;

    public MonitoredDiskFileItem(String fieldName, String contentType, boolean isFormField, String fileName,
                                 int sizeThreshold, File repository, OutputStreamListener listener) {
        super(fieldName, contentType, isFormField, fileName, sizeThreshold, repository);
        this.listener = listener;
    }

    public OutputStream getOutputStream() throws IOException {
        if (mos == null) {
            mos = new MonitoredOutputStream(super.getOutputStream(), listener);
        }
        return mos;
    }
}
