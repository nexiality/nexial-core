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

public class ExcelMappingConfig {

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
        @SerializedName("fileHeaderMap-value-to-column")
        private String mapvaluetocolumn;
        @SerializedName("data-type-def-column")
        private String datatypedefcolumn;
        @SerializedName("field-length-def-column")
        private String fieldlengthdefcolumn;
        @SerializedName("position-from-def-column")
        private String positionfromdefcolumn;
        @SerializedName("position-to-def-column")
        private String positiontodefcolumn;
        @SerializedName("alignment-def-column")
        private String alignmentdefcolumn;

        public String getStrategy() { return strategy;}

        public void setStrategy(String strategy) { this.strategy = strategy;}

        public String getMapvaluetocolumn() { return mapvaluetocolumn;}

        public void setMapvaluetocolumn(String mapvaluetocolumn) { this.mapvaluetocolumn = mapvaluetocolumn;}

        public String getDatatypedefcolumn() { return datatypedefcolumn;}

        public void setDatatypedefcolumn(String datatypedefcolumn) { this.datatypedefcolumn = datatypedefcolumn;}

        public String getFieldlengthdefcolumn() { return fieldlengthdefcolumn;}

        public void setFieldlengthdefcolumn(String fieldlengthdefcolumn) {
            this.fieldlengthdefcolumn = fieldlengthdefcolumn;
        }

        public String getPositionfromdefcolumn() { return positionfromdefcolumn;}

        public void setPositionfromdefcolumn(String positionfromdefcolumn) {
            this.positionfromdefcolumn = positionfromdefcolumn;
        }

        public String getPositiontodefcolumn() { return positiontodefcolumn;}

        public void setPositiontodefcolumn(String positiontodefcolumn) {
            this.positiontodefcolumn = positiontodefcolumn;
        }

        public String getAlignmentdefcolumn() { return alignmentdefcolumn;}

        public void setAlignmentdefcolumn(String alignmentdefcolumn) { this.alignmentdefcolumn = alignmentdefcolumn;}
    }

    public static class FileheaderrecordBean {

        @SerializedName("sheet-name")
        private String sheetname;
        @SerializedName("check-column")
        private String checkcolumn;
        @SerializedName("precede-by")
        private String precedeby;
        @SerializedName("follow-by")
        private String followby;
        @SerializedName("ends-on-blank-row")
        private boolean endsonblankrow;
        @SerializedName("record-id-field")
        private String recordidfield;
        @SerializedName("record-id")
        private String recordid;
        @SerializedName("field-separator")
        private String fieldseparator;
        private List<ValidationsBean> validations;
        @SerializedName("fileHeaderMap-functions")
        private List<MapfunctionsBean> mapfunctions;

        public String getRecordid() {
            return recordid;
        }

        public void setRecordid(String recordid) {
            this.recordid = recordid;
        }

        public String getSheetname() { return sheetname;}

        public void setSheetname(String sheetname) { this.sheetname = sheetname;}

        public String getCheckcolumn() { return checkcolumn;}

        public void setCheckcolumn(String checkcolumn) { this.checkcolumn = checkcolumn;}

        public String getPrecedeby() { return precedeby;}

        public void setPrecedeby(String precedeby) { this.precedeby = precedeby;}

        public String getFollowby() { return followby;}

        public void setFollowby(String followby) { this.followby = followby;}

        public boolean isEndsonblankrow() { return endsonblankrow;}

        public void setEndsonblankrow(boolean endsonblankrow) { this.endsonblankrow = endsonblankrow;}

        public String getRecordidfield() { return recordidfield;}

        public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

        public String getFieldseparator() { return fieldseparator;}

        public void setFieldseparator(String fieldseparator) { this.fieldseparator = fieldseparator;}

        public List<ValidationsBean> getValidations() {
            return validations;
        }

        public void setValidations(List<ValidationsBean> validations) {
            this.validations = validations;
        }

        public List<MapfunctionsBean> getMapfunctions() {
            return mapfunctions;
        }

        public void setMapfunctions(List<MapfunctionsBean> mapfunctions) {
            this.mapfunctions = mapfunctions;
        }
    }

    public static class FilefooterrecordBean {

        @SerializedName("sheet-name")
        private String sheetname;
        @SerializedName("check-column")
        private String checkcolumn;
        @SerializedName("precede-by")
        private String precedeby;
        @SerializedName("follow-by")
        private String followby;
        @SerializedName("ends-on-blank-row")
        private boolean endsonblankrow;
        @SerializedName("record-id-field")
        private String recordidfield;
        @SerializedName("record-id")
        private String recordid;
        @SerializedName("field-separator")
        private String fieldseparator;
        private List<ValidationsBean> validations;
        @SerializedName("fileHeaderMap-functions")
        private List<MapfunctionsBean> mapfunctions;

        public String getRecordid() {
            return recordid;
        }

        public void setRecordid(String recordid) {
            this.recordid = recordid;
        }

        public List<ValidationsBean> getValidations() {
            return validations;
        }

        public void setValidations(List<ValidationsBean> validations) {
            this.validations = validations;
        }

        public List<MapfunctionsBean> getMapfunctions() {
            return mapfunctions;
        }

        public void setMapfunctions(List<MapfunctionsBean> mapfunctions) {
            this.mapfunctions = mapfunctions;
        }

        public String getSheetname() { return sheetname;}

        public void setSheetname(String sheetname) { this.sheetname = sheetname;}

        public String getCheckcolumn() { return checkcolumn;}

        public void setCheckcolumn(String checkcolumn) { this.checkcolumn = checkcolumn;}

        public String getPrecedeby() { return precedeby;}

        public void setPrecedeby(String precedeby) { this.precedeby = precedeby;}

        public String getFollowby() { return followby;}

        public void setFollowby(String followby) { this.followby = followby;}

        public boolean isEndsonblankrow() { return endsonblankrow;}

        public void setEndsonblankrow(boolean endsonblankrow) { this.endsonblankrow = endsonblankrow;}

        public String getRecordidfield() { return recordidfield;}

        public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

        public String getFieldseparator() { return fieldseparator;}

        public void setFieldseparator(String fieldseparator) { this.fieldseparator = fieldseparator;}
    }

   /* public static class ValidationsBean {

        @SerializedName("field-name")
        private String fieldname;
        @SerializedName("validation-methods")
        private List<ValidationsBean.ValidationmethodsBean> validationmethods;

        public static class ValidationmethodsBean {

            private String type;
            private JsonElement params;

            public String getType() { return type;}

            public void setType(String type) { this.type = type;}

            public JsonElement getParams() {
                return params;
            }

            public void setParams(JsonElement params) {
                this.params = params;
            }
        }

        public String getFieldname() { return fieldname;}

        public void setFieldname(String fieldname) { this.fieldname = fieldname;}

        public List<ValidationsBean.ValidationmethodsBean> getValidationmethods() { return validationmethods;}

        public void setValidationmethods(List<ValidationsBean.ValidationmethodsBean> validationmethods) {
            this.validationmethods = validationmethods;
        }
    }*/

    /*public static class MapfunctionsBean {

        @SerializedName("field-name")
        private String fieldname;
        @SerializedName("sign-field")
        private String signfield;
        @SerializedName("function")
        private String function;
        @SerializedName("mapTo")
        private String mapTo;

        public String getSignfield() {
            return signfield;
        }

        public void setSignfield(String signfield) {
            this.signfield = signfield;
        }

        public String getFieldname() { return fieldname;}

        public void setFieldname(String fieldname) { this.fieldname = fieldname;}

        public String getFunction() { return function;}

        public void setFunction(String function) { this.function = function;}

        public String getMapTo() { return mapTo;}

        public void setMapTo(String mapTo) { this.mapTo = mapTo;}
    }*/

    public static class FilesectionsBean {

        @SerializedName("header-record")
        private HeaderrecordBean headerrecord;
        @SerializedName("footer-record")
        private FooterrecordBean footerrecord;
        @SerializedName("body-records")
        private List<BodyrecordsBean> bodyrecords;

        public static class HeaderrecordBean {

            @SerializedName("sheet-name")
            private String sheetname;
            @SerializedName("check-column")
            private String checkcolumn;
            @SerializedName("precede-by")
            private String precedeby;
            @SerializedName("follow-by")
            private String followby;
            @SerializedName("ends-on-blank-row")
            private boolean endsonblankrow;
            @SerializedName("record-id-field")
            private String recordidfield;
            @SerializedName("record-id")
            private String recordid;
            @SerializedName("field-separator")
            private String fieldseparator;
            @SerializedName("validations")
            private List<ValidationsBean> validations;
            @SerializedName("fileHeaderMap-functions")
            private List<MapfunctionsBean> mapfunctions;

            public String getRecordid() {
                return recordid;
            }

            public void setRecordid(String recordid) {
                this.recordid = recordid;
            }

            public List<ValidationsBean> getValidations() {
                return validations;
            }

            public void setValidations(List<ValidationsBean> validations) {
                this.validations = validations;
            }

            public List<MapfunctionsBean> getMapfunctions() {
                return mapfunctions;
            }

            public void setMapfunctions(List<MapfunctionsBean> mapfunctions) {
                this.mapfunctions = mapfunctions;
            }

            public String getSheetname() { return sheetname;}

            public void setSheetname(String sheetname) { this.sheetname = sheetname;}

            public String getCheckcolumn() { return checkcolumn;}

            public void setCheckcolumn(String checkcolumn) { this.checkcolumn = checkcolumn;}

            public String getPrecedeby() { return precedeby;}

            public void setPrecedeby(String precedeby) { this.precedeby = precedeby;}

            public String getFollowby() { return followby;}

            public void setFollowby(String followby) { this.followby = followby;}

            public boolean isEndsonblankrow() { return endsonblankrow;}

            public void setEndsonblankrow(boolean endsonblankrow) { this.endsonblankrow = endsonblankrow;}

            public String getRecordidfield() { return recordidfield;}

            public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

            public String getFieldseparator() { return fieldseparator;}

            public void setFieldseparator(String fieldseparator) { this.fieldseparator = fieldseparator;}
        }

        public static class FooterrecordBean {

            @SerializedName("sheet-name")
            private String sheetname;
            @SerializedName("check-column")
            private String checkcolumn;
            @SerializedName("precede-by")
            private String precedeby;
            @SerializedName("follow-by")
            private String followby;
            @SerializedName("ends-on-blank-row")
            private boolean endsonblankrow;
            @SerializedName("record-id-field")
            private String recordidfield;
            @SerializedName("record-id")
            private String recordid;
            @SerializedName("field-separator")
            private String fieldseparator;
            @SerializedName("validations")
            private List<ValidationsBean> validations;
            @SerializedName("fileHeaderMap-functions")
            private List<MapfunctionsBean> mapfunctions;

            public String getRecordid() {
                return recordid;
            }

            public void setRecordid(String recordid) {
                this.recordid = recordid;
            }

            public List<MapfunctionsBean> getMapfunctions() {
                return mapfunctions;
            }

            public void setMapfunctions(List<MapfunctionsBean> mapfunctions) {
                this.mapfunctions = mapfunctions;
            }

            public String getSheetname() { return sheetname;}

            public void setSheetname(String sheetname) { this.sheetname = sheetname;}

            public String getCheckcolumn() { return checkcolumn;}

            public void setCheckcolumn(String checkcolumn) { this.checkcolumn = checkcolumn;}

            public String getPrecedeby() { return precedeby;}

            public void setPrecedeby(String precedeby) { this.precedeby = precedeby;}

            public String getFollowby() { return followby;}

            public void setFollowby(String followby) { this.followby = followby;}

            public boolean isEndsonblankrow() { return endsonblankrow;}

            public void setEndsonblankrow(boolean endsonblankrow) { this.endsonblankrow = endsonblankrow;}

            public String getRecordidfield() { return recordidfield;}

            public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

            public String getFieldseparator() { return fieldseparator;}

            public void setFieldseparator(String fieldseparator) { this.fieldseparator = fieldseparator;}

            public List<ValidationsBean> getValidations() { return validations;}

            public void setValidations(List<ValidationsBean> validations) { this.validations = validations;}
        }

        public static class BodyrecordsBean {

            @SerializedName("sheet-name")
            private String sheetname;
            @SerializedName("check-column")
            private String checkcolumn;
            @SerializedName("precede-by")
            private String precedeby;
            @SerializedName("follow-by")
            private String followby;
            @SerializedName("ends-on-blank-row")
            private boolean endsonblankrow;
            @SerializedName("record-id-field")
            private String recordidfield;
            @SerializedName("record-id")
            private String recordid;
            @SerializedName("field-separator")
            private String fieldseparator;
            @SerializedName("validations")
            private List<ValidationsBean> validations;
            @SerializedName("fileHeaderMap-functions")
            private List<MapfunctionsBean> mapfunctions;

            public String getRecordid() {
                return recordid;
            }

            public void setRecordid(String recordid) {
                this.recordid = recordid;
            }

            public String getSheetname() { return sheetname;}

            public void setSheetname(String sheetname) { this.sheetname = sheetname;}

            public String getCheckcolumn() { return checkcolumn;}

            public void setCheckcolumn(String checkcolumn) { this.checkcolumn = checkcolumn;}

            public String getPrecedeby() { return precedeby;}

            public void setPrecedeby(String precedeby) { this.precedeby = precedeby;}

            public String getFollowby() { return followby;}

            public void setFollowby(String followby) { this.followby = followby;}

            public boolean isEndsonblankrow() { return endsonblankrow;}

            public void setEndsonblankrow(boolean endsonblankrow) { this.endsonblankrow = endsonblankrow;}

            public String getRecordidfield() { return recordidfield;}

            public void setRecordidfield(String recordidfield) { this.recordidfield = recordidfield;}

            public String getFieldseparator() { return fieldseparator;}

            public void setFieldseparator(String fieldseparator) { this.fieldseparator = fieldseparator;}

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
