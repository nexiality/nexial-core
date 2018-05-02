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

package org.nexial.core.plugins.filevalidation.config;

import java.util.List;

import com.google.gson.annotations.SerializedName;

public class JsonMappingConfig {

    @SerializedName("mapping-config")
    private MappingconfigBean mappingconfig;
    @SerializedName("file-header-record")
    private FileheaderrecordBean fileheaderrecord;
    @SerializedName("file-footer-record")
    private FilefooterrecordBean filefooterrecord;
    @SerializedName("file-sections")
    private List<FilesectionsBean> filesections;

    public static class MappingconfigBean {

        private String strategy;

        public String getStrategy() { return strategy;}

        public void setStrategy(String strategy) { this.strategy = strategy;}
    }



    public static class RecordspecBean {

        @SerializedName("field-name")
        private String fieldname;
        @SerializedName("data-type")
        private String datatype;
        @SerializedName("field-length")
        private int fieldlength;
        @SerializedName("position-from")
        private int positionfrom;
        @SerializedName("position-to")
        private int positionto;
        private String alignment;
        private String comments;

        public String getFieldname() { return fieldname;}

        public void setFieldname(String fieldname) { this.fieldname = fieldname;}

        public String getDatatype() { return datatype;}

        public void setDatatype(String datatype) { this.datatype = datatype;}

        public int getFieldlength() { return fieldlength;}

        public void setFieldlength(int fieldlength) { this.fieldlength = fieldlength;}

        public int getPositionfrom() { return positionfrom;}

        public void setPositionfrom(int positionfrom) { this.positionfrom = positionfrom;}

        public int getPositionto() { return positionto;}

        public void setPositionto(int positionto) { this.positionto = positionto;}

        public String getAlignment() { return alignment;}

        public void setAlignment(String alignment) { this.alignment = alignment;}

        public String getComments() { return comments;}

        public void setComments(String comments) { this.comments = comments;}
    }

    public static class FileheaderrecordBean {

        @SerializedName("record-id-field")
        private String recordidfield;
        @SerializedName("record-id")
        private String recordid;
        @SerializedName("field-separator")
        private String fieldSeparator;
        @SerializedName("record-spec")
        private List<RecordspecBean> recordspec;
        private List<ValidationsBean> validations;
        @SerializedName("map-functions")
        private List<MapfunctionsBean> mapfunctions;

        public String getRecordidfield() { return recordidfield;}

        public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

        public String getRecordid() { return recordid;}

        public void setRecordid(String recordid) { this.recordid = recordid;}

        public String getFieldSeparator() {
            return fieldSeparator;
        }

        public void setFieldSeparator(String fieldSeparator) {
            this.fieldSeparator = fieldSeparator;
        }

        public List<RecordspecBean> getRecordspec() { return recordspec;}

        public void setRecordspec(List<RecordspecBean> recordspec) { this.recordspec = recordspec;}

        public List<ValidationsBean> getValidations() { return validations;}

        public void setValidations(List<ValidationsBean> validations) { this.validations = validations;}

        public List<MapfunctionsBean> getMapfunctions() { return mapfunctions;}

        public void setMapfunctions(List<MapfunctionsBean> mapfunctions) { this.mapfunctions = mapfunctions;}
    }

    public static class FilefooterrecordBean {

        @SerializedName("record-id-field")
        private String recordidfield;
        @SerializedName("record-id")
        private String recordid;
        @SerializedName("field-separator")
        private String fieldSeparator;
        @SerializedName("record-spec")
        private List<RecordspecBean> recordspec;
        private List<ValidationsBean> validations;
        @SerializedName("map-functions")
        private List<MapfunctionsBean> mapfunctions;

        public String getRecordidfield() { return recordidfield;}

        public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

        public String getRecordid() { return recordid;}

        public void setRecordid(String recordid) { this.recordid = recordid;}

        public String getFieldSeparator() {
            return fieldSeparator;
        }

        public void setFieldSeparator(String fieldSeparator) {
            this.fieldSeparator = fieldSeparator;
        }

        public List<RecordspecBean> getRecordspec() { return recordspec;}

        public void setRecordspec(List<RecordspecBean> recordspec) { this.recordspec = recordspec;}

        public List<ValidationsBean> getValidations() { return validations;}

        public void setValidations(List<ValidationsBean> validations) { this.validations = validations;}

        public List<MapfunctionsBean> getMapfunctions() { return mapfunctions;}

        public void setMapfunctions(List<MapfunctionsBean> mapfunctions) { this.mapfunctions = mapfunctions;}
    }

    public static class FilesectionsBean {

        @SerializedName("header-record")
        private HeaderrecordBean headerrecord;
        @SerializedName("footer-record")
        private FooterrecordBean footerrecord;
        @SerializedName("body-records")
        private List<BodyrecordsBean> bodyrecords;

        public static class HeaderrecordBean {

            @SerializedName("record-id-field")
            private String recordidfield;
            @SerializedName("record-id")
            private String recordid;
            @SerializedName(("field-separator"))
            private String fieldSeparator;
            @SerializedName("record-spec")
            private List<RecordspecBean> recordspec;
            private List<ValidationsBean> validations;
            @SerializedName("map-functions")
            private List<MapfunctionsBean> mapfunctions;

            public String getFieldSeparator() {
                return fieldSeparator;
            }

            public void setFieldSeparator(String fieldSeparator) {
                this.fieldSeparator = fieldSeparator;
            }

            public String getRecordidfield() { return recordidfield;}

            public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

            public String getRecordid() { return recordid;}

            public void setRecordid(String recordid) { this.recordid = recordid;}

            public List<RecordspecBean> getRecordspec() { return recordspec;}

            public void setRecordspec(List<RecordspecBean> recordspec) { this.recordspec = recordspec;}

            public List<ValidationsBean> getValidations() { return validations;}

            public void setValidations(List<ValidationsBean> validations) { this.validations = validations;}

            public List<MapfunctionsBean> getMapfunctions() { return mapfunctions;}

            public void setMapfunctions(List<MapfunctionsBean> mapfunctions) { this.mapfunctions = mapfunctions;}
        }

        public static class FooterrecordBean {

            @SerializedName("record-id-field")
            private String recordidfield;
            @SerializedName("record-id")
            private String recordid;
            @SerializedName("field-separator")
            private String fieldSeparator;
            @SerializedName("record-spec")
            private List<RecordspecBean> recordspec;
            private List<ValidationsBean> validations;
            @SerializedName("map-functions")
            private List<MapfunctionsBean> mapfunctions;

            public String getFieldSeparator() {
                return fieldSeparator;
            }

            public void setFieldSeparator(String fieldSeparator) {
                this.fieldSeparator = fieldSeparator;
            }

            public String getRecordidfield() { return recordidfield;}

            public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

            public String getRecordid() { return recordid;}

            public void setRecordid(String recordid) { this.recordid = recordid;}

            public List<RecordspecBean> getRecordspec() { return recordspec;}

            public void setRecordspec(List<RecordspecBean> recordspec) { this.recordspec = recordspec;}

            public List<ValidationsBean> getValidations() { return validations;}

            public void setValidations(List<ValidationsBean> validations) { this.validations = validations;}

            public List<MapfunctionsBean> getMapfunctions() { return mapfunctions;}

            public void setMapfunctions(List<MapfunctionsBean> mapfunctions) { this.mapfunctions = mapfunctions;}
        }

        public static class BodyrecordsBean {

            @SerializedName("record-id-field")
            private String recordidfield;
            @SerializedName("record-id")
            private String recordid;
            @SerializedName("field-separator")
            private String fieldSeparator;
            @SerializedName("record-spec")
            private List<RecordspecBean> recordspec;
            private List<ValidationsBean> validations;
            @SerializedName("map-functions")
            private List<MapfunctionsBean> mapfunctions;

            public String getFieldSeparator() {
                return fieldSeparator;
            }

            public void setFieldSeparator(String fieldSeparator) {
                this.fieldSeparator = fieldSeparator;
            }

            public String getRecordidfield() { return recordidfield;}

            public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

            public String getRecordid() { return recordid;}

            public void setRecordid(String recordid) { this.recordid = recordid;}

            public List<RecordspecBean> getRecordspec() { return recordspec;}

            public void setRecordspec(List<RecordspecBean> recordspec) { this.recordspec = recordspec;}

            public List<ValidationsBean> getValidations() { return validations;}

            public void setValidations(List<ValidationsBean> validations) { this.validations = validations;}

            public List<MapfunctionsBean> getMapfunctions() { return mapfunctions;}

            public void setMapfunctions(List<MapfunctionsBean> mapfunctions) { this.mapfunctions = mapfunctions;}
        }

        public HeaderrecordBean getHeaderrecord() { return headerrecord;}

        public void setHeaderrecord(HeaderrecordBean headerrecord) { this.headerrecord = headerrecord;}

        public FooterrecordBean getFooterrecord() { return footerrecord;}

        public void setFooterrecord(FooterrecordBean footerrecord) { this.footerrecord = footerrecord;}

        public List<BodyrecordsBean> getBodyrecords() { return bodyrecords;}

        public void setBodyrecords(List<BodyrecordsBean> bodyrecords) { this.bodyrecords = bodyrecords;}
    }

    public MappingconfigBean getMappingconfig() { return mappingconfig;}

    public void setMappingconfig(MappingconfigBean mappingconfig) { this.mappingconfig = mappingconfig;}

    public FileheaderrecordBean getFileheaderrecord() { return fileheaderrecord;}

    public void setFileheaderrecord(FileheaderrecordBean fileheaderrecord) { this.fileheaderrecord = fileheaderrecord;}

    public FilefooterrecordBean getFilefooterrecord() { return filefooterrecord;}

    public void setFilefooterrecord(FilefooterrecordBean filefooterrecord) { this.filefooterrecord = filefooterrecord;}

    public List<FilesectionsBean> getFilesections() { return filesections;}

    public void setFilesections(List<FilesectionsBean> filesections) { this.filesections = filesections;}
}
