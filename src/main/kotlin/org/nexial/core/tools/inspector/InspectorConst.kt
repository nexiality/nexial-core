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

package org.nexial.core.tools.inspector

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.charset.Charset
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object InspectorConst {
    val GSON: Gson = GsonBuilder().setLenient().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create()
    val UTF8: Charset = Charset.forName("UTF-8")
    val LOG_DATE_FORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S")

    const val PROJECT_ID = ".meta/project.id"
    const val LOCAL_HTML_RESOURCE = "/org/nexial/core/reports/project-inspector-local.html"
    const val MACRO_DESCRIPTION = "macro.description()"
    const val MACRO_EXPECTS = "macro.expects(var,default)"
    const val MACRO_PRODUCES = "macro.produces(var,value)"
    val MACRO_CMDS: MutableList<String> = Arrays.asList(MACRO_DESCRIPTION, MACRO_EXPECTS, MACRO_PRODUCES)

    // todo: not sustainable.. we need better approach. annontation maybe?
    val VAR_CMDS = mapOf(
            "aws.s3.copyFrom(var,profile,remote,local)" to 0,
            "aws.s3.copyTo(var,profile,local,remote)" to 0,
            "aws.s3.delete(var,profile,remotePath)" to 0,
            "aws.s3.list(var,profile,remotePath)" to 0,
            "aws.s3.moveFrom(var,profile,remote,local)" to 0,
            "aws.s3.moveTo(var,profile,local,remote)" to 0,
            "base.appendText(var,appendWith)" to 0,
            "base.prependText(var,prependWith)" to 0,
            "base.incrementChar(var,amount,config)" to 0,
            "base.save(var,value)" to 0,
            "base.saveCount(text,regex,saveVar)" to 2,
            "base.saveMatches(text,regex,saveVar)" to 2,
            "base.saveReplace(text,regex,replace,saveVar)" to 3,
            "base.saveVariablesByPrefix(var,prefix)" to 0,
            "base.saveVariablesByRegex(var,regex)" to 0,
            "base.split(text,delim,saveVar)" to 2,
            "base.substringAfter(text,delim,saveVar)" to 2,
            "base.substringBefore(text,delim,saveVar)" to 2,
            "base.substringBetween(text,start,end,saveVar)" to 4,
            "csv.compareExtended(var,profile,expected,actual)" to 0,
            "desktop.clearModalDialog(var,button)" to 0,
            "desktop.clickTextPaneRow(var,index)" to 0,
            "desktop.editHierCells(var,matchBy,nameValues)" to 0,
            "desktop.getRowCount(var)" to 0,
            "desktop.saveAllTableRows(var)" to 0,
            "desktop.saveAttributeByLocator(var,locator,attribute)" to 0,
            "desktop.saveElementCount(var,name)" to 0,
            "desktop.saveFirstListData(var,contains)" to 0,
            "desktop.saveFirstMatchedListIndex(var,contains)" to 0,
            "desktop.saveHierCells(var,matchBy,column,nestedOnly)" to 0,
            "desktop.saveHierRow(var,matchBy)" to 0,
            "desktop.saveListData(var,contains)" to 0,
            "desktop.saveLocatorCount(var,locator)" to 0,
            "desktop.saveModalDialogText(var)" to 0,
            "desktop.saveModalDialogTextByLocator(var,locater)" to 0,
            "desktop.saveProcessId(var,locator)" to 0,
            "desktop.saveRowCount(var)" to 0,
            "desktop.saveTableRows(var,contains)" to 0,
            "desktop.saveTableRowsRange(var,beginRow,endRow)" to 0,
            "desktop.saveText(var,name)" to 0,
            "desktop.saveTextPane(var,name,criteria)" to 0,
            "desktop.saveWindowTitle(var)" to 0,
            "desktop.scanTable(var,name)" to 0,
            "desktop.useHierTable(var,name)" to 0,
            "desktop.useList(var,name)" to 0,
            "desktop.useTable(var,name)" to 0,
            "desktop.useTableRow(var,row)" to 0,
            "excel.saveData(var,file,worksheet,range)" to 0,
            "excel.saveRange(var,file,worksheet,range)" to 0,
            "excel.writeVar(var,file,worksheet,startCell)" to 0,
            "io.base64(var,file)" to 0,
            "io.count(var,path,pattern)" to 0,
            "io.readFile(var,file)" to 0,
            "io.readProperty(var,file,property)" to 0,
            "io.saveDiff(var,expected,actual)" to 0,
            "io.saveFileMeta(var,file)" to 0,
            "io.saveMatches(var,path,filePattern)" to 0,
            "io.validate(var,profile,inputFile)" to 0,
            "jms.receive(var,config,waitMs)" to 0,
            "json.addOrReplace(json,jsonpath,input,var)" to 3,
            "json.beautify(json,var)" to 1,
            "json.minify(json,var)" to 1,
            "json.storeCount(json,jsonpath,var)" to 2,
            "json.storeValue(json,jsonpath,var)" to 2,
            "json.storeValues(json,jsonpath,var)" to 2,
            "macro.produces(var,value)" to 0,
            "number.average(var,array)" to 0,
            "number.ceiling(var)" to 0,
            "number.decrement(var,amount)" to 0,
            "number.floor(var)" to 0,
            "number.increment(var,amount)" to 0,
            "number.max(var,array)" to 0,
            "number.min(var,array)" to 0,
            "number.round(var,closestDigit)" to 0,
            "pdf.count(pdf,text,var)" to 2,
            "pdf.saveFormValues(pdf,var,pageAndLineStartEnd,strategy)" to 1,
            "pdf.saveMetadata(pdf,var)" to 1,
            "pdf.saveToVar(pdf,var)" to 1,
            "rdbms.runFile(var,db,file)" to 0,
            "rdbms.runSQL(var,db,sql)" to 0,
            "rdbms.runSQLs(var,db,sqls)" to 0,
            "redis.store(var,profile,key)" to 0,
            "redis.storeKeys(var,profile,keyPattern)" to 0,
            "ssh.scpCopyFrom(var,profile,remote,local)" to 0,
            "ssh.scpCopyTo(var,profile,local,remote)" to 0,
            "ssh.sftpCopyFrom(var,profile,remote,local)" to 0,
            "ssh.sftpCopyTo(var,profile,local,remote)" to 0,
            "ssh.sftpDelete(var,profile,remote)" to 0,
            "ssh.sftpList(var,profile,remote)" to 0,
            "ssh.sftpMoveFrom(var,profile,remote,local)" to 0,
            "ssh.sftpMoveTo(var,profile,local,remote)" to 0,
            "web.executeScript(var,script)" to 0,
            "web.saveAllWindowIds(var)" to 0,
            "web.saveAllWindowNames(var)" to 0,
            "web.saveAttribute(var,locator,attrName)" to 0,
            "web.saveAttributeList(var,locator,attrName)" to 0,
            "web.saveCount(var,locator)" to 0,
            "web.saveElement(var,locator)" to 0,
            "web.saveElements(var,locator)" to 0,
            "web.saveLocalStorage(var,key)" to 0,
            "web.saveLocation(var)" to 0,
            "web.savePageAs(var,sessionIdName,url)" to 0,
            "web.saveText(var,locator)" to 0,
            "web.saveTextArray(var,locator)" to 0,
            "web.saveTextSubstringAfter(var,locator,delim)" to 0,
            "web.saveTextSubstringBefore(var,locator,delim)" to 0,
            "web.saveTextSubstringBetween(var,locator,start,end)" to 0,
            "web.saveValue(var,locator)" to 0,
            "webalert.storeText(var)" to 0,
            "webcookie.save(var,name)" to 0,
            "webcookie.saveAll(var)" to 0,
            "ws.delete(url,body,var)" to 2,
            "ws.get(url,queryString,var)" to 2,
            "ws.head(url,var)" to 1,
            "ws.headerByVar(name,var)" to 1,
            "ws.jwtParse(var,token,key)" to 0,
            "ws.jwtSignHS256(var,payload,key)" to 0,
            "ws.oauth(var,url,auth)" to 0,
            "ws.patch(url,body,var)" to 2,
            "ws.post(url,body,var)" to 2,
            "ws.put(url,body,var)" to 2,
            "ws.saveResponsePayload(var,file,append)" to 0,
            "ws.soap(action,url,payload,var)" to 3,
            "ws.upload(url,body,fileParams,var)" to 3,
            "xml.beautify(xml,var)" to 1,
            "xml.minify(xml,var)" to 1,
            "xml.storeCount(xml,xpath,var)" to 2,
            "xml.storeValue(xml,xpath,var)" to 2,
            "xml.storeValues(xml,xpath,var)" to 2)

    val MULTI_VARS_CMDS = mapOf("base.clear(vars)" to 0)

    object ReturnCode {
        const val MISSING_DIRECTORY = -2
        const val MISSING_OUTPUT = -3
        const val BAD_DIRECTORY = -4
        const val BAD_OUTPUT = -5
        const val WRITE_FILE = -6
        const val READ_JSON = -7
    }

    fun exit(returnCode: Int) = System.exit(returnCode)
}
