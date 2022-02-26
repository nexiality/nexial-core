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

package org.nexial.core.plugins.xml;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;

import java.io.File;

import static org.nexial.core.NexialConst.DEF_FILE_ENCODING;
import static org.nexial.core.NexialConst.TEMP;

public class XmlCommandTest {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                 "<CATALOG>\n" +
                 "    <CD>\n" +
                 "        <TITLE>Empire Burlesque</TITLE>\n" +
                 "        <ARTIST>Bob Dylan</ARTIST>\n" +
                 "        <COUNTRY>USA</COUNTRY>\n" +
                 "        <COMPANY>Columbia</COMPANY>\n" +
                 "        <PRICE>10.90</PRICE>\n" +
                 "        <YEAR>1985</YEAR>\n" +
                 "    </CD>\n" +
                 "    <CD>\n" +
                 "        <TITLE>Eros</TITLE>\n" +
                 "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                 "        <COUNTRY>EU</COUNTRY>\n" +
                 "        <COMPANY>BMG</COMPANY>\n" +
                 "        <PRICE>9.90</PRICE>\n" +
                 "        <YEAR>1997</YEAR>\n" +
                 "    </CD>\n" +
                 "</CATALOG>\n";
    private MockExecutionContext context;

    @Before
    public void init() {
        context = new MockExecutionContext();
    }

    @After
    public void tearDown() {
        if (context != null) { context.cleanProject(); }
    }

    @Test
    public void testAssertElementPresent() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml =
            "<catalog>" +
            "     <book id=\"bk101\">" +
            "          <author>Gambardella, Matthew</author>" +
            "          <title>XML Developer's Guide</title>" +
            "          <genre>Computer</genre>" +
            "          <price>44.95</price>" +
            "          <publish_date>2000-10-01</publish_date>" +
            "          <description>An in-depth look at creating applications" +
            "               with XML." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk102\">" +
            "          <author>Ralls, Kim</author>" +
            "          <title>Midnight Rain</title>" +
            "          <genre>Fantasy</genre>" +
            "          <price>5.95</price>" +
            "          <publish_date>2000-12-16</publish_date>" +
            "          <description>A former architect battles corporate zombies," +
            "               an evil sorceress, and her own childhood to become queen" +
            "               of the world." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk103\">" +
            "          <author>Corets, Eva</author>" +
            "          <title>Maeve Ascendant</title>" +
            "          <genre>Fantasy</genre>" +
            "          <price>5.95</price>" +
            "          <publish_date>2000-11-17</publish_date>" +
            "          <description>After the collapse of a nanotechnology" +
            "               society in England, the young survivors lay the" +
            "               foundation for a new society." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk104\">" +
            "          <author>Corets, Eva</author>" +
            "          <title>Oberon's Legacy</title>" +
            "          <genre>Fantasy</genre>" +
            "          <price>5.95</price>" +
            "          <publish_date>2001-03-10</publish_date>" +
            "          <description>In post-apocalypse England, the mysterious" +
            "               agent known only as Oberon helps to create a new life" +
            "               for the inhabitants of London. Sequel to Maeve" +
            "               Ascendant." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk105\">" +
            "          <author>Corets, Eva</author>" +
            "          <title>The Sundered Grail</title>" +
            "          <genre>Fantasy</genre>" +
            "          <price>5.95</price>" +
            "          <publish_date>2001-09-10</publish_date>" +
            "          <description>The two daughters of Maeve, half-sisters," +
            "               battle one another for control of England. Sequel to" +
            "               Oberon's Legacy." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk106\">" +
            "          <author>Randall, Cynthia</author>" +
            "          <title>Lover Birds</title>" +
            "          <genre>Romance</genre>" +
            "          <price>4.95</price>" +
            "          <publish_date>2000-09-02</publish_date>" +
            "          <description>When Carla meets Paul at an ornithology" +
            "               conference, tempers fly as feathers get ruffled." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk107\">" +
            "          <author>Thurman, Paula</author>" +
            "          <title>Splish Splash</title>" +
            "          <genre>Romance</genre>" +
            "          <price>4.95</price>" +
            "          <publish_date>2000-11-02</publish_date>" +
            "          <description>A deep sea diver finds true love twenty" +
            "               thousand leagues beneath the sea." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk108\">" +
            "          <author>Knorr, Stefan</author>" +
            "          <title>Creepy Crawlies</title>" +
            "          <genre>Horror</genre>" +
            "          <price>4.95</price>" +
            "          <publish_date>2000-12-06</publish_date>" +
            "          <description>An anthology of horror stories about roaches," +
            "               centipedes, scorpions and other insects." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk109\">" +
            "          <author>Kress, Peter</author>" +
            "          <title>Paradox Lost</title>" +
            "          <genre>Science Fiction</genre>" +
            "          <price>6.95</price>" +
            "          <publish_date>2000-11-02</publish_date>" +
            "          <description>After an inadvertant trip through a Heisenberg" +
            "               Uncertainty Device, James Salway discovers the problems" +
            "               of being quantum." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk110\">" +
            "          <author>O'Brien, Tim</author>" +
            "          <title>Microsoft .NET: The Programming Bible</title>" +
            "          <genre>Computer</genre>" +
            "          <price>36.95</price>" +
            "          <publish_date>2000-12-09</publish_date>" +
            "          <description>Microsoft's .NET initiative is explored in" +
            "               detail in this deep programmer's reference." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk111\">" +
            "          <author>O'Brien, Tim</author>" +
            "          <title>MSXML3: A Comprehensive Guide</title>" +
            "          <genre>Computer</genre>" +
            "          <price>36.95</price>" +
            "          <publish_date>2000-12-01</publish_date>" +
            "          <description>The Microsoft MSXML3 parser is covered in" +
            "               detail, with attention to XML DOM interfaces, XSLT processing," +
            "               SAX and more." +
            "          </description>" +
            "     </book>" +
            "     <book id=\"bk112\">" +
            "          <author>Galos, Mike</author>" +
            "          <title>Visual Studio 7: A Comprehensive Guide</title>" +
            "          <genre>Computer</genre>" +
            "          <price>49.95</price>" +
            "          <publish_date>2001-04-16</publish_date>" +
            "          <description>Microsoft Visual Studio 7 is explored in depth," +
            "               looking at how Visual Basic, Visual C++, C#, and ASP+ are" +
            "               integrated into a comprehensive development" +
            "               environment." +
            "          </description>" +
            "     </book>" +
            "</catalog>";

        Assert.assertTrue(fixture.assertElementPresent(xml,
                                                       "//catalog/book[@id='bk104']/genre[text()='Fantasy']/following-sibling::price[text()='5.95']")
                                 .isSuccess());
        String xpath = "//price[text()>44]";
        System.out.println("xpath = " + xpath);
        StepResult anyMoreThan44 = fixture.assertElementPresent(xml, xpath);
        //Assert.assertTrue(anyMoreThan44.isSuccess());
        System.out.println("anyMoreThan44 = " + anyMoreThan44);
        String moreThan44 = fixture.getValuesByXPath(xml, xpath);
        System.out.println("moreThan44 = " + moreThan44);
    }

    @Test
    public void testGetValueByXPath() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml =
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:sam=\"http://www.example.org/sample/\">"
            + "   <soapenv:Header/>"
            + "   <soapenv:Body>"
            + "      <sam:logoutResponse>"
            + "         <sessioninfo>OK</sessioninfo>"
            + "      </sam:logoutResponse>"
            + "   </soapenv:Body>"
            + "</soapenv:Envelope>";
        String xpath;
        Assert.assertEquals("OK",
                            fixture.getValueByXPath(xml,
                                                    "//*[local-name()='Envelope']/*[local-name()='Body']/*[local-name()='logoutResponse']/sessioninfo"));
        Assert.assertEquals("OK", fixture.getValueByXPath(xml, "//sessioninfo"));
        Assert.assertEquals("",
                            fixture.getValueByXPath(xml,
                                                    "//*[local-name()='Envelope']/*[local-name()='Body']/*[local-name()='logoutResponse']"));

        xml =
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:sam=\"http://www.example.org/sample/\">"
            + "   <soapenv:Header/>"
            + "   <soapenv:Body>"
            + "      <sam:searchResponse>"
            + "         <sam:searchResponse>"
            + "            <item>"
            + "               <id>Item 1</id>"
            + "               <description>One handy protocol droid. This droid is fluent "
            + "\t\tin over six million forms of communication and has a lovely golden color. "
            + "\t\tBuilt by an enthusiast. Mindwiped only once. Can be carried on your back.</description>"
            + "               <price>1</price>"
            + "            </item>"
            + "            <item>"
            + "               <id new=\"true\">Item 2</id>"
            + "               <description>Yada yada</description>"
            + "               <price>12</price>"
            + "            </item>"
            + "         </sam:searchResponse>"
            + "      </sam:searchResponse>"
            + "   </soapenv:Body>"
            + "</soapenv:Envelope>";

        xpath = "//item/price";
        Assert.assertEquals("1", fixture.getValueByXPath(xml, xpath));

        xpath = "//item[2]/price";
        Assert.assertEquals("12", fixture.getValueByXPath(xml, xpath));

        xpath = "//item/id[@new='true']/following-sibling::price";
        Assert.assertEquals("12", fixture.getValueByXPath(xml, xpath));
    }

    @Test
    public void testGetValueByXPath2() throws Exception {
        XmlCommand subject = new XmlCommand();
        subject.init(context);

        String xpath = "string(/configuration/appSettings/add[@key='AppVersionMarker']/@value)";
        String resourcePath = StringUtils.replace(getClass().getPackage().getName(), ".", "/");

        // test case #1
        String xml = ResourceUtils.loadResource(resourcePath + "/XmlCommandTest1.xml");
        Assert.assertTrue(StringUtils.isNotBlank(xml));

        String value = subject.getValueByXPath(xml, xpath);
        Assert.assertNotNull(value);
        Assert.assertEquals("1.9.19-14596", value);

        // test case #2
        xml = ResourceUtils.loadResource(resourcePath + "/XmlCommandTest2.xml");
        Assert.assertTrue(StringUtils.isNotBlank(xml));

        value = subject.getValueByXPath(xml, xpath);
        Assert.assertNotNull(value);
        Assert.assertEquals("1.6.4.10264", value);

        // test case #3
        xml = StringUtils.trim(ResourceUtils.loadResource(resourcePath + "/XmlCommandTest3.xml"));
        xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + StringUtils.substringAfter(xml,
                                                                                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        Assert.assertTrue(StringUtils.isNotBlank(xml));

        value = subject.getValueByXPath(xml, xpath);
        Assert.assertNotNull(value);
        Assert.assertEquals("1.9.19-14596", value);
    }

    @Test
    public void testGetValuesByXPath() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml =
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:sam=\"http://www.example.org/sample/\">"
            + "   <soapenv:Header/>"
            + "   <soapenv:Body>"
            + "      <sam:searchResponse>"
            + "         <sam:searchResponse>"
            + "            <item>"
            + "               <id>Item 1</id>"
            + "               <description>One handy protocol droid. This droid is fluent "
            + "\t\tin over six million forms of communication and has a lovely golden color. "
            + "\t\tBuilt by an enthusiast. Mindwiped only once. Can be carried on your back.</description>"
            + "               <price>1</price>"
            + "            </item>"
            + "            <item>"
            + "               <id new=\"true\">Item 2</id>"
            + "               <description>Yada yada</description>"
            + "               <price>12</price>"
            + "            </item>"
            + "         </sam:searchResponse>"
            + "      </sam:searchResponse>"
            + "   </soapenv:Body>"
            + "</soapenv:Envelope>";
        Assert.assertEquals("Item 1,Item 2", fixture.getValuesByXPath(xml, "//id"));
        Assert.assertEquals("1,12", fixture.getValuesByXPath(xml, "//item/price"));

        xml = "<a>" +
              " <b>" +
              "     <c>H</c>" +
              "     <c>e</c>" +
              "     <c>l</c>" +
              "     <c>l</c>" +
              "     <c>o</c>" +
              " </b>" +
              "</a>";
        Assert.assertEquals("H,e,l,l,o", fixture.getValuesByXPath(xml, "//a/b/c"));
        Assert.assertEquals("e,l", fixture.getValuesByXPath(xml, "//a/b/c[position()>1 and position()<4]"));

        xml = "<a>" +
              "     <b>" +
              "          <c>H</c>" +
              "          <c>e</c>" +
              "          <c>l</c>" +
              "          <c>l</c>" +
              "          <c>" +
              "               o" +
              "               <d>Bye</d>" +
              "          </c>" +
              "     </b>" +
              "</a>";
        Assert.assertEquals("H,e,l,l,o", fixture.getValuesByXPath(xml, "//a/b/c"));
        Assert.assertEquals("e,l", fixture.getValuesByXPath(xml, "//a/b/c[position()>1 and position()<4]"));
        Assert.assertEquals("Bye", fixture.getValuesByXPath(xml, "//a/b/c/d"));
    }

    @Test
    public void testCount() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<a>" +
                     "     <b>" +
                     "          <c>H</c>" +
                     "          <c>e</c>" +
                     "          <c>l</c>" +
                     "          <c>l</c>" +
                     "          <c>" +
                     "               o" +
                     "               <d>Bye</d>" +
                     "          </c>" +
                     "     </b>" +
                     "</a>";
        Assert.assertEquals(5, fixture.count(xml, "//a/b/c"));
        Assert.assertEquals(2, fixture.count(xml, "//a/b/c[position()>1 and position()<4]"));
        Assert.assertEquals(1, fixture.count(xml, "//a/b/c/d"));

    }

    @Test
    public void testWellformed() {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<a>" +
                     "     <b>" +
                     "          <c>H</c>" +
                     "          <c>e</c>" +
                     "          <c>l</c>" +
                     "          <c>l</c>" +
                     "          <c>" +
                     "               o" +
                     "               <d>Bye</d>" +
                     "          </c>" +
                     "     </b>" +
                     "</a>";
        Assert.assertTrue(fixture.assertWellformed(xml).isSuccess());
        // whitespaces
        Assert.assertTrue(fixture.assertWellformed("\t\t \n   \r \n \t    \n" +
                                                   xml + "     \t \t\t \n \r \r \n  \t \n\n")
                                 .isSuccess());
        // add new element
        Assert.assertTrue(fixture.assertWellformed(StringUtils.replace(xml, "</b>", "</b><c>I'm here</c>"))
                                 .isSuccess());
    }

    @Test
    public void testCorrectness() {
        String schema = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                        "<xs:element name=\"shiporder\">\n" +
                        "  <xs:complexType>\n" +
                        "    <xs:sequence>\n" +
                        "      <xs:element name=\"orderperson\" type=\"xs:string\"/>\n" +
                        "      <xs:element name=\"shipto\">\n" +
                        "        <xs:complexType>\n" +
                        "          <xs:sequence>\n" +
                        "            <xs:element name=\"name\" type=\"xs:string\"/>\n" +
                        "            <xs:element name=\"address\" type=\"xs:string\"/>\n" +
                        "            <xs:element name=\"city\" type=\"xs:string\"/>\n" +
                        "            <xs:element name=\"country\" type=\"xs:string\"/>\n" +
                        "          </xs:sequence>\n" +
                        "        </xs:complexType>\n" +
                        "      </xs:element>\n" +
                        "      <xs:element name=\"item\" maxOccurs=\"unbounded\">\n" +
                        "        <xs:complexType>\n" +
                        "          <xs:sequence>\n" +
                        "            <xs:element name=\"title\" type=\"xs:string\"/>\n" +
                        "            <xs:element name=\"note\" type=\"xs:string\" minOccurs=\"0\"/>\n" +
                        "            <xs:element name=\"quantity\" type=\"xs:positiveInteger\"/>\n" +
                        "            <xs:element name=\"price\" type=\"xs:decimal\"/>\n" +
                        "          </xs:sequence>\n" +
                        "        </xs:complexType>\n" +
                        "      </xs:element>\n" +
                        "    </xs:sequence>\n" +
                        "    <xs:attribute name=\"orderid\" type=\"xs:string\" use=\"required\"/>\n" +
                        "  </xs:complexType>\n" +
                        "</xs:element>\n" +
                        "</xs:schema>";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<shiporder orderid=\"889923\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                     "  <orderperson>John Smith</orderperson>\n" +
                     "  <shipto>\n" +
                     "    <name>Ola Nordmann</name>\n" +
                     "    <address>Langgt 23</address>\n" +
                     "    <city>4000 Stavanger</city>\n" +
                     "    <country>Norway</country>\n" +
                     "  </shipto>\n" +
                     "  <item>\n" +
                     "    <title>Empire Burlesque</title>\n" +
                     "    <note>Special Edition</note>\n" +
                     "    <quantity>1</quantity>\n" +
                     "    <price>10.90</price>\n" +
                     "  </item>\n" +
                     "  <item>\n" +
                     "    <title>Hide your heart</title>\n" +
                     "    <quantity>1</quantity>\n" +
                     "    <price>9.90</price>\n" +
                     "  </item>\n" +
                     "</shiporder>";

        XmlCommand fixture = new XmlCommand();
        fixture.init(context);
        Assert.assertTrue(fixture.assertCorrectness(xml, schema).isSuccess());
    }

    @Test
    public void append_text() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        StepResult result = fixture.append(xml, "//CATALOG/CD/TITLE", " Joker", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque Joker</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros Joker</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void append_element() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        // test: add multiple element
        StepResult result = fixture.append(xml, "//CATALOG/CD/TITLE",
                                           "<SUBTITLE>Joker</SUBTITLE><SEASON>First</SEASON>",
                                           "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            Empire Burlesque\n" +
                            "            <SUBTITLE>Joker</SUBTITLE>\n" +
                            "            <SEASON>First</SEASON>\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            Eros\n" +
                            "            <SUBTITLE>Joker</SUBTITLE>\n" +
                            "            <SEASON>First</SEASON>\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void append_text_element_again_text() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        // test: add content and then element, and then again text content
        StepResult result = fixture.append(xml, "//CATALOG/CD/TITLE", " - New Edition", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque - New Edition</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros - New Edition</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.append(newXml,
                                "//CATALOG/CD/TITLE",
                                "<SUBTITLE>Bigger, Stronger, Faster</SUBTITLE>",
                                "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            Empire Burlesque - New Edition\n" +
                            "            <SUBTITLE>Bigger, Stronger, Faster</SUBTITLE>\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            Eros - New Edition\n" +
                            "            <SUBTITLE>Bigger, Stronger, Faster</SUBTITLE>\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.append(newXml, "//CATALOG/CD/TITLE", "Collector's Favorite!", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            Empire Burlesque - New Edition\n" +
                            "            <SUBTITLE>Bigger, Stronger, Faster</SUBTITLE>\n" +
                            "            Collector's Favorite!\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            Eros - New Edition\n" +
                            "            <SUBTITLE>Bigger, Stronger, Faster</SUBTITLE>\n" +
                            "            Collector's Favorite!\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void append_attribute() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST>Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE currency=\"USD\">10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Eros</TITLE>\n" +
                     "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                     "        <COUNTRY>EU</COUNTRY>\n" +
                     "        <COMPANY>BMG</COMPANY>\n" +
                     "        <PRICE currency=\"IND\">9.90</PRICE>\n" +
                     "        <YEAR>1997</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.append(xml, "//CATALOG/CD/PRICE/@currency", ",SGD", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE currency=\"USD,SGD\">10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE currency=\"IND,SGD\">9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void prepend_text() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        StepResult result = fixture.prepend(xml, "//CATALOG/CD/TITLE", "Reader's Digest - ", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Reader's Digest - Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Reader's Digest - Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void prepend_attribute() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST>Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE currency=\"USD\">10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Eros</TITLE>\n" +
                     "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                     "        <COUNTRY>EU</COUNTRY>\n" +
                     "        <COMPANY>BMG</COMPANY>\n" +
                     "        <PRICE currency=\"IND\">9.90</PRICE>\n" +
                     "        <YEAR>1997</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.prepend(xml, "//CATALOG/CD/PRICE/@currency", "BITCOIN,", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE currency=\"BITCOIN,USD\">10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE currency=\"BITCOIN,IND\">9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void replace_text() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        StepResult result = fixture.replace(xml, "//CATALOG/CD/YEAR", "UNKNOWN", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        UNKNOWN\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        UNKNOWN\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void prepend_text_element_multipleElement() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        // test: add content and then element, and then again text content
        StepResult result = fixture.prepend(xml, "//CATALOG/CD/TITLE", "Guardian: ", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Guardian: Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Guardian: Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.prepend(newXml, "//CATALOG/CD/TITLE", "<AWARD>Bookworm Recommends</AWARD>", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            <AWARD>Bookworm Recommends</AWARD>\n" +
                            "            Guardian: Empire Burlesque\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            <AWARD>Bookworm Recommends</AWARD>\n" +
                            "            Guardian: Eros\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.prepend(newXml, "//CATALOG/CD[position()=1]/TITLE",
                                 "<AWARD>Miami Herald</AWARD><AWARD>Laureus Sports</AWARD>", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            <AWARD>Miami Herald</AWARD>\n" +
                            "            <AWARD>Laureus Sports</AWARD>\n" +
                            "            <AWARD>Bookworm Recommends</AWARD>\n" +
                            "            Guardian: Empire Burlesque\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>\n" +
                            "            <AWARD>Bookworm Recommends</AWARD>\n" +
                            "            Guardian: Eros\n" +
                            "        </TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void replace_attribute() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST>Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE currency=\"USD\">10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Eros</TITLE>\n" +
                     "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                     "        <COUNTRY>EU</COUNTRY>\n" +
                     "        <COMPANY>BMG</COMPANY>\n" +
                     "        <PRICE currency=\"IND\">9.90</PRICE>\n" +
                     "        <YEAR>1997</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.replace(xml, "//CATALOG/CD/PRICE/@currency", "bitcoin", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE bitcoin=\"USD\">10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE bitcoin=\"IND\">9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void replace_multiple() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        // test: add content and then element, and then again text content
        StepResult result = fixture.replace(xml, "//CATALOG/CD", "<CD><MESSAGE>Not available</MESSAGE></CD>", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <MESSAGE>Not available</MESSAGE>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <MESSAGE>Not available</MESSAGE>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.replace(newXml, "//CATALOG/CD/MESSAGE", "Not available at this time", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>Not available at this time</CD>\n" +
                            "    <CD>Not available at this time</CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.replace(newXml, "//CATALOG/CD[position()=1]", "<CD id=\"123\">Not available</CD>", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD id=\"123\">Not available</CD>\n" +
                            "    <CD>Not available at this time</CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void delete_attribute() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST alive=\"no\">Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE>10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Eros</TITLE>\n" +
                     "        <ARTIST alive=\"yes\">Eros Ramazzotti</ARTIST>\n" +
                     "        <COUNTRY>EU</COUNTRY>\n" +
                     "        <COMPANY>BMG</COMPANY>\n" +
                     "        <PRICE>9.90</PRICE>\n" +
                     "        <YEAR>1997</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.delete(xml, "//CATALOG/CD[ ARTIST[@alive=\"no\"] ]", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST alive=\"yes\">Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.delete(xml, "//CATALOG/CD/ARTIST[@alive=\"no\"]/@alive", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST alive=\"yes\">Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void delete_element() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST alive=\"no\">Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE>10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Eros</TITLE>\n" +
                     "        <ARTIST alive=\"yes\">Eros Ramazzotti</ARTIST>\n" +
                     "        <COUNTRY>EU</COUNTRY>\n" +
                     "        <COMPANY>BMG</COMPANY>\n" +
                     "        <PRICE>9.90</PRICE>\n" +
                     "        <YEAR>1997</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.delete(xml, "//CATALOG/CD/*[name() != 'TITLE' and name() != 'ARTIST']", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST alive=\"no\">Bob Dylan</ARTIST>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST alive=\"yes\">Eros Ramazzotti</ARTIST>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void clear_element() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST alive=\"no\">Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE>10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Eros</TITLE>\n" +
                     "        <ARTIST alive=\"yes\">Eros Ramazzotti</ARTIST>\n" +
                     "        <COUNTRY>EU</COUNTRY>\n" +
                     "        <COMPANY>BMG</COMPANY>\n" +
                     "        <PRICE>9.90</PRICE>\n" +
                     "        <YEAR>1997</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.clear(xml, "//CATALOG/CD/*[name() != 'TITLE' and name() != 'ARTIST']", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST alive=\"no\">Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY />\n" +
                            "        <COMPANY />\n" +
                            "        <PRICE />\n" +
                            "        <YEAR />\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST alive=\"yes\">Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY />\n" +
                            "        <COMPANY />\n" +
                            "        <PRICE />\n" +
                            "        <YEAR />\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.clear(xml, "//CATALOG/CD", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD />\n" +
                            "    <CD />\n" +
                            "</CATALOG>", newXml);

    }

    @Test
    public void insertAfter_text_element_multipleElement() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        // test: add content and then element, and then again text content
        StepResult result = fixture.insertAfter(xml, "//CATALOG/CD[position()=2]", "MIAMI AWARDS", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "    MIAMI AWARDS\n" +
                            "</CATALOG>", newXml);

        result = fixture.insertAfter(newXml, "//CATALOG/CD/ARTIST", "<AWARD>Bookworm Recommends</AWARD>", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <AWARD>Bookworm Recommends</AWARD>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <AWARD>Bookworm Recommends</AWARD>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "    MIAMI AWARDS\n" +
                            "</CATALOG>", newXml);

        result = fixture.insertAfter(newXml, "//CATALOG/CD[position()=1]/ARTIST",
                                     "<AWARD>Miami Herald</AWARD><AWARD>Laureus Sports</AWARD>", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <AWARD>Miami Herald</AWARD>\n" +
                            "        <AWARD>Laureus Sports</AWARD>\n" +
                            "        <AWARD>Bookworm Recommends</AWARD>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <AWARD>Bookworm Recommends</AWARD>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "    MIAMI AWARDS\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void insertAfter_attribute() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST>Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE currency=\"USD\">10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.insertAfter(xml, "//CATALOG/CD/PRICE/@currency", "city", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE currency=\"USD\" city=\"\">10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.insertAfter(newXml, "//CATALOG/CD/PRICE/@currency", "city=NYC", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE currency=\"USD\" city=\"NYC\">10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void insertBefore_text_element_multipleElement() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        // test: add content and then element, and then again text content
        StepResult result = fixture.insertBefore(xml, "//CATALOG/CD[position()=1]", "MIAMI AWARDS", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    MIAMI AWARDS\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.insertBefore(newXml, "//CATALOG/CD/ARTIST", "<AWARD>Bookworm Recommends</AWARD>", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    MIAMI AWARDS\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <AWARD>Bookworm Recommends</AWARD>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <AWARD>Bookworm Recommends</AWARD>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.insertBefore(newXml, "//CATALOG/CD[position()=1]/ARTIST",
                                      "<AWARD>Miami Herald</AWARD><AWARD>Laureus Sports</AWARD>", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    MIAMI AWARDS\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <AWARD>Bookworm Recommends</AWARD>\n" +
                            "        <AWARD>Miami Herald</AWARD>\n" +
                            "        <AWARD>Laureus Sports</AWARD>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE>10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <AWARD>Bookworm Recommends</AWARD>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE>9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void insertBefore_attribute() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST>Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE currency=\"USD\">10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.insertBefore(xml, "//CATALOG/CD/PRICE/@currency", "city", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE currency=\"USD\" city=\"\">10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.insertBefore(newXml, "//CATALOG/CD/PRICE/@currency", "city=NYC", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE currency=\"USD\" city=\"NYC\">10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void replaceIn_attribute() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<CATALOG>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Empire Burlesque</TITLE>\n" +
                     "        <ARTIST>Bob Dylan</ARTIST>\n" +
                     "        <COUNTRY>USA</COUNTRY>\n" +
                     "        <COMPANY>Columbia</COMPANY>\n" +
                     "        <PRICE currency=\"USD\">10.90</PRICE>\n" +
                     "        <YEAR>1985</YEAR>\n" +
                     "    </CD>\n" +
                     "    <CD>\n" +
                     "        <TITLE>Eros</TITLE>\n" +
                     "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                     "        <COUNTRY>EU</COUNTRY>\n" +
                     "        <COMPANY>BMG</COMPANY>\n" +
                     "        <PRICE currency=\"IND\">9.90</PRICE>\n" +
                     "        <YEAR>1997</YEAR>\n" +
                     "    </CD>\n" +
                     "</CATALOG>\n";

        StepResult result = fixture.replaceIn(xml, "//CATALOG/CD/PRICE/@currency", "bitcoin", "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Empire Burlesque</TITLE>\n" +
                            "        <ARTIST>Bob Dylan</ARTIST>\n" +
                            "        <COUNTRY>USA</COUNTRY>\n" +
                            "        <COMPANY>Columbia</COMPANY>\n" +
                            "        <PRICE currency=\"bitcoin\">10.90</PRICE>\n" +
                            "        <YEAR>1985</YEAR>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <TITLE>Eros</TITLE>\n" +
                            "        <ARTIST>Eros Ramazzotti</ARTIST>\n" +
                            "        <COUNTRY>EU</COUNTRY>\n" +
                            "        <COMPANY>BMG</COMPANY>\n" +
                            "        <PRICE currency=\"bitcoin\">9.90</PRICE>\n" +
                            "        <YEAR>1997</YEAR>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);
    }

    @Test
    public void replaceIn_multiple() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        // test: add text content and then element
        StepResult result = fixture.replaceIn(xml,
                                              "//CATALOG/CD",
                                              "<STATUS>404</STATUS>Not available at this time",
                                              "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <STATUS>404</STATUS>\n" +
                            "        Not available at this time\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <STATUS>404</STATUS>\n" +
                            "        Not available at this time\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

        result = fixture.replaceIn(newXml, "//CATALOG/CD",
                                   "<STATUS>404</STATUS><MESSAGE>Not available</MESSAGE>",
                                   "newXml");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        newXml = context.getStringData("newXml");
        System.out.println("newXml = " + newXml);
        Assert.assertEquals("<CATALOG>\n" +
                            "    <CD>\n" +
                            "        <STATUS>404</STATUS>\n" +
                            "        <MESSAGE>Not available</MESSAGE>\n" +
                            "    </CD>\n" +
                            "    <CD>\n" +
                            "        <STATUS>404</STATUS>\n" +
                            "        <MESSAGE>Not available</MESSAGE>\n" +
                            "    </CD>\n" +
                            "</CATALOG>", newXml);

    }

    @Test
    public void assertSoapFaultCode_simple() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        StepResult result = fixture.assertSoapFaultCode("soap:Server",
                                                        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                                        "   <soap:Body>" +
                                                        "       <soap:Fault>\n" +
                                                        "           <faultcode>soap:Server</faultcode>\n" +
                                                        "           <faultstring>Conversion to SOAP failed</faultstring>\n" +
                                                        "           <detail>\n" +
                                                        "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                                        "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                                        "               generation failed because of incorrect input \n" +
                                                        "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                                        "               </CICSFault> \n" +
                                                        "           </detail> \n" +
                                                        "       </soap:Fault>\n" +
                                                        "   </soap:Body>  \n" +
                                                        "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void assertSoapFaultCode_none() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        StepResult result = fixture.assertSoapFaultCode("(empty)",
                                                        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                                        "   <soap:Body>" +
                                                        "       <soap:Fault>\n" +
                                                        "           <detail>\n" +
                                                        "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                                        "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                                        "               generation failed because of incorrect input \n" +
                                                        "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                                        "               </CICSFault> \n" +
                                                        "           </detail> \n" +
                                                        "       </soap:Fault>\n" +
                                                        "   </soap:Body>  \n" +
                                                        "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        result = fixture.assertSoapFaultCode("",
                                             "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                             "   <soap:Body>" +
                                             "       <soap:Fault>\n" +
                                             "           <faultstring>Conversion to SOAP failed</faultstring>\n" +
                                             "           <detail>\n" +
                                             "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                             "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                             "               generation failed because of incorrect input \n" +
                                             "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                             "               </CICSFault> \n" +
                                             "           </detail> \n" +
                                             "       </soap:Fault>\n" +
                                             "   </soap:Body>  \n" +
                                             "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        result = fixture.assertSoapFaultCode("",
                                             "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                             "   <soap:Body>" +
                                             "       <soap:Fault>\n" +
                                             "           <faultcode></faultcode>\n" +
                                             "           <detail>\n" +
                                             "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                             "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                             "               generation failed because of incorrect input \n" +
                                             "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                             "               </CICSFault> \n" +
                                             "           </detail> \n" +
                                             "       </soap:Fault>\n" +
                                             "   </soap:Body>  \n" +
                                             "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        result = fixture.assertSoapFaultCode("",
                                             "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                             "   <soap:Body>" +
                                             "       <soap:Fault>\n" +
                                             "           <faultcode><SomeOtherNode></SomeOtherNode></faultcode>\n" +
                                             "           <faultstring>Conversion to SOAP failed</faultstring>\n" +
                                             "           <detail>\n" +
                                             "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                             "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                             "               generation failed because of incorrect input \n" +
                                             "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                             "               </CICSFault> \n" +
                                             "           </detail> \n" +
                                             "       </soap:Fault>\n" +
                                             "   </soap:Body>  \n" +
                                             "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

    }

    @Test
    public void assertSoapFaultCode_fail() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        try {
            fixture.assertSoapFaultCode("soap:Server",
                                        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                        "   <soap:Body>" +
                                        "       <soap:Fault>\n" +
                                        "           <faultcode/>\n" +
                                        "           <faultstring>Conversion to SOAP failed</faultstring>\n" +
                                        "           <detail>\n" +
                                        "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                        "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                        "               generation failed because of incorrect input \n" +
                                        "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                        "               </CICSFault> \n" +
                                        "           </detail> \n" +
                                        "       </soap:Fault>\n" +
                                        "   </soap:Body>  \n" +
                                        "</soap:Envelope>");
            Assert.fail("expected AssertionError NOT thrown");
        } catch (AssertionError e) {
            System.out.println("EXPECTED: " + e.getMessage());
        }

        try {
            fixture.assertSoapFaultCode("Server",
                                        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                        "   <soap:Body>" +
                                        "       <soap:Fault>\n" +
                                        "           <faultcode>soap:Server</faultcode>\n" +
                                        "           <faultstring>Conversion to SOAP failed</faultstring>\n" +
                                        "           <detail>\n" +
                                        "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                        "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                        "               generation failed because of incorrect input \n" +
                                        "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                        "               </CICSFault> \n" +
                                        "           </detail> \n" +
                                        "       </soap:Fault>\n" +
                                        "   </soap:Body>  \n" +
                                        "</soap:Envelope>");
            Assert.fail("expected AssertionError NOT thrown");
        } catch (AssertionError e) {
            System.out.println("EXPECTED: " + e.getMessage());
        }
    }

    @Test
    public void assertSoapFaultString_simple() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        StepResult result = fixture.assertSoapFaultString("Conversion to SOAP failed",
                                                          "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                                          "   <soap:Body>" +
                                                          "       <soap:Fault>\n" +
                                                          "           <faultcode>soap:Server</faultcode>\n" +
                                                          "           <faultstring>Conversion to SOAP failed</faultstring>\n" +
                                                          "           <detail>\n" +
                                                          "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                                          "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                                          "               generation failed because of incorrect input \n" +
                                                          "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                                          "               </CICSFault> \n" +
                                                          "           </detail> \n" +
                                                          "       </soap:Fault>\n" +
                                                          "   </soap:Body>  \n" +
                                                          "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());
    }

    @Test
    public void assertSoapFaultString_none() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        StepResult result = fixture.assertSoapFaultString("(empty)",
                                                          "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                                          "   <soap:Body>" +
                                                          "       <soap:Fault>\n" +
                                                          "           <detail>\n" +
                                                          "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                                          "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                                          "               generation failed because of incorrect input \n" +
                                                          "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                                          "               </CICSFault> \n" +
                                                          "           </detail> \n" +
                                                          "       </soap:Fault>\n" +
                                                          "   </soap:Body>  \n" +
                                                          "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        result = fixture.assertSoapFaultString("",
                                               "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                               "   <soap:Body>" +
                                               "       <soap:Fault>\n" +
                                               "           <faultstring></faultstring>\n" +
                                               "           <detail>\n" +
                                               "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                               "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                               "               generation failed because of incorrect input \n" +
                                               "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                               "               </CICSFault> \n" +
                                               "           </detail> \n" +
                                               "       </soap:Fault>\n" +
                                               "   </soap:Body>  \n" +
                                               "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        result = fixture.assertSoapFaultString("",
                                               "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                               "   <soap:Body>" +
                                               "       <soap:Fault>\n" +
                                               "           <faultstring/>\n" +
                                               "           <detail>\n" +
                                               "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                               "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                               "               generation failed because of incorrect input \n" +
                                               "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                               "               </CICSFault> \n" +
                                               "           </detail> \n" +
                                               "       </soap:Fault>\n" +
                                               "   </soap:Body>  \n" +
                                               "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        result = fixture.assertSoapFaultString("",
                                               "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                               "   <soap:Body>" +
                                               "       <soap:Fault>\n" +
                                               "           <faultcode>CODE RED</faultcode>\n" +
                                               "           <faultstring><SomeOtherNode></SomeOtherNode></faultstring>\n" +
                                               "           <detail>\n" +
                                               "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                               "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                               "               generation failed because of incorrect input \n" +
                                               "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                               "               </CICSFault> \n" +
                                               "           </detail> \n" +
                                               "       </soap:Fault>\n" +
                                               "   </soap:Body>  \n" +
                                               "</soap:Envelope>");
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

    }

    @Test
    public void assertSoapFaultString_fail() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        try {
            fixture.assertSoapFaultString("soap:Server",
                                          "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                          "   <soap:Body>" +
                                          "       <soap:Fault>\n" +
                                          "           <faultcode/>\n" +
                                          "           <faultstring/>\n" +
                                          "           <detail>\n" +
                                          "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                          "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                          "               generation failed because of incorrect input \n" +
                                          "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                          "               </CICSFault> \n" +
                                          "           </detail> \n" +
                                          "       </soap:Fault>\n" +
                                          "   </soap:Body>  \n" +
                                          "</soap:Envelope>");
            Assert.fail("expected AssertionError NOT thrown");
        } catch (AssertionError e) {
            System.out.println("EXPECTED: " + e.getMessage());
        }

        try {
            fixture.assertSoapFaultString("Server",
                                          "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                                          "   <soap:Body>" +
                                          "       <soap:Fault>\n" +
                                          "           <faultcode>soap:Server</faultcode>\n" +
                                          "           <faultstring>Conversion to SOAP failed</faultstring>\n" +
                                          "           <detail>\n" +
                                          "               <CICSFault xmlns:soap=\"http://www.ibm.com/software/htp/cics/WSFault\">\n" +
                                          "               DFHPI1008 25/01/2010 14:16:50 IYCWZCFU 00340 XML\n" +
                                          "               generation failed because of incorrect input \n" +
                                          "               (CONTAINER_NOT_FOUND container name) for WEBSERVICE servicename. \n" +
                                          "               </CICSFault> \n" +
                                          "           </detail> \n" +
                                          "       </soap:Fault>\n" +
                                          "   </soap:Body>  \n" +
                                          "</soap:Envelope>");
            Assert.fail("expected AssertionError NOT thrown");
        } catch (AssertionError e) {
            System.out.println("EXPECTED: " + e.getMessage());
        }
    }

    @Test
    public void assertSoap_simple() throws Exception {
        XmlCommand fixture = new XmlCommand();
        fixture.init(context);

        String wsdl = "<?xml version=\"1.0\"?>\n" +
                      "<wsdl:definitions name=\"EndorsementSearch\" targetNamespace=\"http://namespaces.snowboard-info.com\" xmlns:es=\"http://www.snowboard-info.com/EndorsementSearch.wsdl\" xmlns:esxsd=\"http://schemas.snowboard-info.com/EndorsementSearch.xsd\" xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.snowboard-info.com/EndorsementSearch.wsdl\">\n" +
                      "  <wsdl:types>\n" +
                      "    <xsd:schema targetNamespace=\"http://namespaces.snowboard-info.com\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.w3.org/2001/XMLSchema \">\n" +
                      "      <xsd:element name=\"GetEndorsingBoarder\">\n" +
                      "        <xsd:complexType>\n" +
                      "          <xsd:sequence>\n" +
                      "            <xsd:element name=\"manufacturer\" type=\"xsd:string\"/>\n" +
                      "            <xsd:element name=\"model\" type=\"xsd:string\"/>\n" +
                      "          </xsd:sequence>\n" +
                      "        </xsd:complexType>\n" +
                      "      </xsd:element>\n" +
                      "      <xsd:element name=\"GetEndorsingBoarderResponse\">\n" +
                      "        <xsd:complexType>\n" +
                      "          <xsd:all>\n" +
                      "            <xsd:element name=\"endorsingBoarder\" type=\"xsd:string\"/>\n" +
                      "          </xsd:all>\n" +
                      "        </xsd:complexType>\n" +
                      "      </xsd:element>\n" +
                      "      <xsd:element name=\"GetEndorsingBoarderFault\">\n" +
                      "        <xsd:complexType>\n" +
                      "          <xsd:all>\n" +
                      "            <xsd:element name=\"errorMessage\" type=\"xsd:string\"/>\n" +
                      "          </xsd:all>\n" +
                      "        </xsd:complexType>\n" +
                      "      </xsd:element>\n" +
                      "    </xsd:schema>\n" +
                      "  </wsdl:types>\n" +
                      "  <wsdl:message name=\"GetEndorsingBoarderRequest\">\n" +
                      "    <wsdl:part name=\"body\" element=\"esxsd:GetEndorsingBoarder\"/>\n" +
                      "  </wsdl:message>\n" +
                      "  <wsdl:message name=\"GetEndorsingBoarderResponse\">\n" +
                      "    <wsdl:part name=\"body\" element=\"esxsd:GetEndorsingBoarderResponse\"/>\n" +
                      "  </wsdl:message>\n" +
                      "  <wsdl:portType name=\"GetEndorsingBoarderPortType\">\n" +
                      "    <wsdl:operation name=\"GetEndorsingBoarder\">\n" +
                      "      <wsdl:input message=\"es:GetEndorsingBoarderRequest\"/>\n" +
                      "      <wsdl:output message=\"es:GetEndorsingBoarderResponse\"/>\n" +
                      "      <wsdl:fault message=\"es:GetEndorsingBoarderFault\"/>\n" +
                      "    </wsdl:operation>\n" +
                      "  </wsdl:portType>\n" +
                      "  <wsdl:binding name=\"EndorsementSearchSoapBinding\" type=\"es:GetEndorsingBoarderPortType\">\n" +
                      "    <soap:binding style=\"document\" transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
                      "    <wsdl:operation name=\"GetEndorsingBoarder\">\n" +
                      "      <soap:operation soapAction=\"http://www.snowboard-info.com/EndorsementSearch\"/>\n" +
                      "      <wsdl:input>\n" +
                      "        <soap:body use=\"literal\" namespace=\"http://schemas.snowboard-info.com/EndorsementSearch.xsd\"/>\n" +
                      "      </wsdl:input>\n" +
                      "      <wsdl:output>\n" +
                      "        <soap:body use=\"literal\" namespace=\"http://schemas.snowboard-info.com/EndorsementSearch.xsd\"/>\n" +
                      "      </wsdl:output>\n" +
                      "      <wsdl:fault>\n" +
                      "        <soap:body use=\"literal\" namespace=\"http://schemas.snowboard-info.com/EndorsementSearch.xsd\"/>\n" +
                      "      </wsdl:fault>\n" +
                      "    </wsdl:operation>\n" +
                      "  </wsdl:binding>\n" +
                      "  <wsdl:service name=\"EndorsementSearchService\">\n" +
                      "    <wsdl:documentation>snowboarding-info.com Endorsement Service</wsdl:documentation>\n" +
                      "    <wsdl:port name=\"GetEndorsingBoarderPort\" binding=\"es:EndorsementSearchSoapBinding\">\n" +
                      "      <soap:address location=\"http://www.snowboard-info.com/EndorsementSearch\"/>\n" +
                      "    </wsdl:port>\n" +
                      "  </wsdl:service>\n" +
                      "</wsdl:definitions>\n";
        File wsdlFile = new File(TEMP + "myWSDL.xsd");
        FileUtils.writeStringToFile(wsdlFile, wsdl, DEF_FILE_ENCODING);

        String requestXml =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" soap:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
            "  <soap:Body>\n" +
            "    <m:GetEndorsingBoarder xmlns:m=\"http://namespaces.snowboard-info.com\">\n" +
            "      <manufacturer>K2</manufacturer>\n" +
            "      <model>Fatbob</model>\n" +
            "    </m:GetEndorsingBoarder>\n" +
            "  </soap:Body>\n" +
            "</soap:Envelope>";

        StepResult result = fixture.assertSoap(wsdlFile.getAbsolutePath(), requestXml);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());

        String responseXml =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" soap:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
            "  <soap:Body>\n" +
            "    <m:GetEndorsingBoarderResponse xmlns:m=\"http://namespaces.snowboard-info.com\">\n" +
            "      <endorsingBoarder>Chris Englesmann</endorsingBoarder>\n" +
            "    </m:GetEndorsingBoarderResponse>\n" +
            "  </soap:Body>\n" +
            "</soap:Envelope>";

        result = fixture.assertSoap(wsdlFile.getAbsolutePath(), responseXml);
        System.out.println("result = " + result);
        Assert.assertTrue(result.isSuccess());
    }

}
