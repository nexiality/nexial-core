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
            "aws.s3.assertNotPresent(profile,remotePath)" to intArrayOf(0),
            "aws.s3.assertPresent(profile,remotePath)" to intArrayOf(0),
            "aws.s3.copyFrom(var,profile,remote,local)" to intArrayOf(0, 1),
            "aws.s3.copyTo(var,profile,local,remote)" to intArrayOf(0, 1),
            "aws.s3.delete(var,profile,remotePath)" to intArrayOf(0, 1),
            "aws.s3.list(var,profile,remotePath)" to intArrayOf(0, 1),
            "aws.s3.moveFrom(var,profile,remote,local)" to intArrayOf(0, 1),
            "aws.s3.moveTo(var,profile,local,remote)" to intArrayOf(0, 1),
            "aws.ses.sendHtmlMail(profile,to,subject,body)" to intArrayOf(0),
            "aws.ses.sendTextMail(profile,to,subject,body)" to intArrayOf(0),
            "aws.sqs.deleteMessage(profile,queue,receiptHandle)" to intArrayOf(0),
            "aws.sqs.receiveMessage(profile,queue,var)" to intArrayOf(2),
            "aws.sqs.receiveMessages(profile,queue,var)" to intArrayOf(2),
            "aws.sqs.sendMessage(profile,queue,message,var)" to intArrayOf(0, 3),
            "base.appendText(var,appendWith)" to intArrayOf(0),
            "base.prependText(var,prependWith)" to intArrayOf(0),
            "base.incrementChar(var,amount,config)" to intArrayOf(0, 2),
            "base.save(var,value)" to intArrayOf(0),
            "base.saveCount(text,regex,saveVar)" to intArrayOf(2),
            "base.saveMatches(text,regex,saveVar)" to intArrayOf(2),
            "base.saveReplace(text,regex,replace,saveVar)" to intArrayOf(3),
            "base.saveVariablesByPrefix(var,prefix)" to intArrayOf(0),
            "base.saveVariablesByRegex(var,regex)" to intArrayOf(0),
            "base.split(text,delim,saveVar)" to intArrayOf(2),
            "base.substringAfter(text,delim,saveVar)" to intArrayOf(2),
            "base.substringBefore(text,delim,saveVar)" to intArrayOf(2),
            "base.substringBetween(text,start,end,saveVar)" to intArrayOf(4),
            "csv.compareExtended(var,profile,expected,actual)" to intArrayOf(0, 1),
            "desktop.clearModalDialog(var,button)" to intArrayOf(0),
            "desktop.clickTextPaneRow(var,index)" to intArrayOf(0),
            "desktop.editHierCells(var,matchBy,nameValues)" to intArrayOf(0),
            "desktop.getRowCount(var)" to intArrayOf(0),
            "desktop.saveAllTableRows(var)" to intArrayOf(0),
            "desktop.saveAttributeByLocator(var,locator,attribute)" to intArrayOf(0),
            "desktop.saveElementCount(var,name)" to intArrayOf(0),
            "desktop.saveFirstListData(var,contains)" to intArrayOf(0),
            "desktop.saveFirstMatchedListIndex(var,contains)" to intArrayOf(0),
            "desktop.saveHierCells(var,matchBy,column,nestedOnly)" to intArrayOf(0),
            "desktop.saveHierRow(var,matchBy)" to intArrayOf(0),
            "desktop.saveListData(var,contains)" to intArrayOf(0),
            "desktop.saveLocatorCount(var,locator)" to intArrayOf(0),
            "desktop.saveModalDialogText(var)" to intArrayOf(0),
            "desktop.saveModalDialogTextByLocator(var,locater)" to intArrayOf(0),
            "desktop.saveProcessId(var,locator)" to intArrayOf(0),
            "desktop.saveRowCount(var)" to intArrayOf(0),
            "desktop.saveTableRows(var,contains)" to intArrayOf(0),
            "desktop.saveTableRowsRange(var,beginRow,endRow)" to intArrayOf(0),
            "desktop.saveText(var,name)" to intArrayOf(0),
            "desktop.saveTextPane(var,name,criteria)" to intArrayOf(0),
            "desktop.saveWindowTitle(var)" to intArrayOf(0),
            "desktop.scanTable(var,name)" to intArrayOf(0),
            "desktop.useHierTable(var,name)" to intArrayOf(0),
            "desktop.useList(var,name)" to intArrayOf(0),
            "desktop.useTable(var,name)" to intArrayOf(0),
            "desktop.useTableRow(var,row)" to intArrayOf(0),
            "excel.saveData(var,file,worksheet,range)" to intArrayOf(0),
            "excel.saveRange(var,file,worksheet,range)" to intArrayOf(0),
            "excel.writeVar(var,file,worksheet,startCell)" to intArrayOf(0),
            "io.base64(var,file)" to intArrayOf(0),
            "io.count(var,path,pattern)" to intArrayOf(0),
            "io.readFile(var,file)" to intArrayOf(0),
            "io.readProperty(var,file,property)" to intArrayOf(0),
            "io.saveDiff(var,expected,actual)" to intArrayOf(0),
            "io.saveFileMeta(var,file)" to intArrayOf(0),
            "io.saveMatches(var,path,filePattern)" to intArrayOf(0),
            "io.searchAndReplace(file,config,saveAs)" to intArrayOf(1),
            "io.validate(var,profile,inputFile)" to intArrayOf(0, 1),
            "jms.receive(var,config,waitMs)" to intArrayOf(0, 1),
            "jms.sendMap(config,id,payload)" to intArrayOf(0),
            "jms.sendText(config,id,payload)" to intArrayOf(0),
            "json.addOrReplace(json,jsonpath,input,var)" to intArrayOf(3),
            "json.beautify(json,var)" to intArrayOf(1),
            "json.minify(json,var)" to intArrayOf(1),
            "json.storeCount(json,jsonpath,var)" to intArrayOf(2),
            "json.storeValue(json,jsonpath,var)" to intArrayOf(2),
            "json.storeValues(json,jsonpath,var)" to intArrayOf(2),
            "macro.produces(var,value)" to intArrayOf(0),
            "mail.send(profile,to,subject,body)" to intArrayOf(0),
            "number.average(var,array)" to intArrayOf(0),
            "number.ceiling(var)" to intArrayOf(0),
            "number.decrement(var,amount)" to intArrayOf(0),
            "number.floor(var)" to intArrayOf(0),
            "number.increment(var,amount)" to intArrayOf(0),
            "number.max(var,array)" to intArrayOf(0),
            "number.min(var,array)" to intArrayOf(0),
            "number.round(var,closestDigit)" to intArrayOf(0),
            "pdf.count(pdf,text,var)" to intArrayOf(2),
            "pdf.saveFormValues(pdf,var,pageAndLineStartEnd,strategy)" to intArrayOf(1),
            "pdf.saveMetadata(pdf,var)" to intArrayOf(1),
            "pdf.saveToVar(pdf,var)" to intArrayOf(1),
            "rdbms.runFile(var,db,file)" to intArrayOf(0, 1),
            "rdbms.runSQL(var,db,sql)" to intArrayOf(0, 1),
            "rdbms.runSQLs(var,db,sqls)" to intArrayOf(0, 1),
            "rdbms.saveResult(db,sql,output)" to intArrayOf(0),
            "rdbms.saveResults(db,sqls,outputDir)" to intArrayOf(0),
            "redis.append(profile,key,value)" to intArrayOf(0),
            "redis.assertKeyExists(profile,key)" to intArrayOf(0),
            "redis.delete(profile,key)" to intArrayOf(0),
            "redis.flushAll(profile)" to intArrayOf(0),
            "redis.flushDb(profile)" to intArrayOf(0),
            "redis.rename(profile,current,new)" to intArrayOf(0),
            "redis.set(profile,key,value)" to intArrayOf(0),
            "redis.store(var,profile,key)" to intArrayOf(0, 1),
            "redis.storeKeys(var,profile,keyPattern)" to intArrayOf(0, 1),
            "ssh.scpCopyFrom(var,profile,remote,local)" to intArrayOf(0, 1),
            "ssh.scpCopyTo(var,profile,local,remote)" to intArrayOf(0, 1),
            "ssh.sftpCopyFrom(var,profile,remote,local)" to intArrayOf(0, 1),
            "ssh.sftpCopyTo(var,profile,local,remote)" to intArrayOf(0, 1),
            "ssh.sftpDelete(var,profile,remote)" to intArrayOf(0, 1),
            "ssh.sftpList(var,profile,remote)" to intArrayOf(0, 1),
            "ssh.sftpMoveFrom(var,profile,remote,local)" to intArrayOf(0, 1),
            "ssh.sftpMoveTo(var,profile,local,remote)" to intArrayOf(0, 1),
            "web.executeScript(var,script)" to intArrayOf(0),
            "web.saveAllWindowIds(var)" to intArrayOf(0),
            "web.saveAllWindowNames(var)" to intArrayOf(0),
            "web.saveAttribute(var,locator,attrName)" to intArrayOf(0),
            "web.saveAttributeList(var,locator,attrName)" to intArrayOf(0),
            "web.saveCount(var,locator)" to intArrayOf(0),
            "web.saveElement(var,locator)" to intArrayOf(0),
            "web.saveElements(var,locator)" to intArrayOf(0),
            "web.saveLocalStorage(var,key)" to intArrayOf(0),
            "web.saveLocation(var)" to intArrayOf(0),
            "web.savePageAs(var,sessionIdName,url)" to intArrayOf(0),
            "web.saveText(var,locator)" to intArrayOf(0),
            "web.saveTextArray(var,locator)" to intArrayOf(0),
            "web.saveTextSubstringAfter(var,locator,delim)" to intArrayOf(0),
            "web.saveTextSubstringBefore(var,locator,delim)" to intArrayOf(0),
            "web.saveTextSubstringBetween(var,locator,start,end)" to intArrayOf(0),
            "web.saveValue(var,locator)" to intArrayOf(0),
            "webalert.storeText(var)" to intArrayOf(0),
            "webcookie.save(var,name)" to intArrayOf(0),
            "webcookie.saveAll(var)" to intArrayOf(0),
            "ws.delete(url,body,var)" to intArrayOf(2),
            "ws.get(url,queryString,var)" to intArrayOf(2),
            "ws.head(url,var)" to intArrayOf(1),
            "ws.headerByVar(name,var)" to intArrayOf(1),
            "ws.jwtParse(var,token,key)" to intArrayOf(0),
            "ws.jwtSignHS256(var,payload,key)" to intArrayOf(0),
            "ws.oauth(var,url,auth)" to intArrayOf(0),
            "ws.patch(url,body,var)" to intArrayOf(2),
            "ws.post(url,body,var)" to intArrayOf(2),
            "ws.put(url,body,var)" to intArrayOf(2),
            "ws.saveResponsePayload(var,file,append)" to intArrayOf(0),
            "ws.soap(action,url,payload,var)" to intArrayOf(3),
            "ws.upload(url,body,fileParams,var)" to intArrayOf(3),
            "xml.append(xml,xpath,content,var)" to intArrayOf(3),
            "xml.beautify(xml,var)" to intArrayOf(1),
            "xml.clear(xml,xpath,var)" to intArrayOf(2),
            "xml.delete(xml,xpath,var)" to intArrayOf(2),
            "xml.minify(xml,var)" to intArrayOf(1),
            "xml.prepend(xml,xpath,content,var)" to intArrayOf(3),
            "xml.replace(xml,xpath,content,var)" to intArrayOf(3),
            "xml.storeCount(xml,xpath,var)" to intArrayOf(2),
            "xml.storeValue(xml,xpath,var)" to intArrayOf(2),
            "xml.storeValues(xml,xpath,var)" to intArrayOf(2))

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
