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

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;

/**
 *
 */
public class MonitoredDiskFileItemFactory extends DiskFileItemFactory {
    private OutputStreamListener listener = null;

    public MonitoredDiskFileItemFactory(OutputStreamListener listener) {
        super();
        this.listener = listener;
    }

    public MonitoredDiskFileItemFactory(int sizeThreshold, File repository, OutputStreamListener listener) {
        super(sizeThreshold, repository);
        this.listener = listener;
    }

    public FileItem createItem(String fieldName, String contentType, boolean isFormField, String fileName) {
        return new MonitoredDiskFileItem(fieldName, contentType, isFormField, fileName, getSizeThreshold(),
                                         getRepository(), listener);
    }
}
