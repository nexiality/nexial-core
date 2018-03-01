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

package org.nexial.core.plugins.xml;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.MockExecutionContext;
import org.nexial.core.model.StepResult;

public class XmlCommandTest {
	private ExecutionContext context = new MockExecutionContext();

	@After
	public void tearDown() {
		if (context != null) { ((MockExecutionContext) context).cleanProject(); }
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
		String xml = ResourceUtils.loadResource(resourcePath + "/nextgen_config.xml");
		Assert.assertTrue(StringUtils.isNotBlank(xml));

		String value = subject.getValueByXPath(xml, xpath);
		Assert.assertNotNull(value);
		Assert.assertEquals("1.9.19-14596", value);

		// test case #2
		xml = ResourceUtils.loadResource(resourcePath + "/nextgen_config2.xml");
		Assert.assertTrue(StringUtils.isNotBlank(xml));

		value = subject.getValueByXPath(xml, xpath);
		Assert.assertNotNull(value);
		Assert.assertEquals("1.6.4.10264", value);

		// test case #3
		xml = StringUtils.trim(ResourceUtils.loadResource(resourcePath + "/nextgen_config3.xml"));
		xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + StringUtils.substringAfter(xml, "<?xml version=\"1.0\" encoding=\"utf-8\"?>");
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

}
