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

package org.nexial.core.excel;

import org.junit.Assert;
import org.junit.Test;

public class ExcelAddressTest {

	@Test
	public void testToLetterCellRef() {
		Assert.assertEquals("A", ExcelAddress.toLetterCellRef(1));
		Assert.assertEquals("B", ExcelAddress.toLetterCellRef(2));
		Assert.assertEquals("M", ExcelAddress.toLetterCellRef(13));
		Assert.assertEquals("Z", ExcelAddress.toLetterCellRef(26));
		Assert.assertEquals("AA", ExcelAddress.toLetterCellRef(27));
		Assert.assertEquals("AB", ExcelAddress.toLetterCellRef(28));
		Assert.assertEquals("AC", ExcelAddress.toLetterCellRef(29));
		Assert.assertEquals("AD", ExcelAddress.toLetterCellRef(30));
		Assert.assertEquals("AE", ExcelAddress.toLetterCellRef(31));
		Assert.assertEquals("AF", ExcelAddress.toLetterCellRef(32));
		Assert.assertEquals("AG", ExcelAddress.toLetterCellRef(33));
		Assert.assertEquals("AH", ExcelAddress.toLetterCellRef(34));
		Assert.assertEquals("AI", ExcelAddress.toLetterCellRef(35));
		Assert.assertEquals("AJ", ExcelAddress.toLetterCellRef(36));
		Assert.assertEquals("AK", ExcelAddress.toLetterCellRef(37));
		Assert.assertEquals("AL", ExcelAddress.toLetterCellRef(38));
		Assert.assertEquals("AM", ExcelAddress.toLetterCellRef(39));
		Assert.assertEquals("AN", ExcelAddress.toLetterCellRef(40));
		Assert.assertEquals("AO", ExcelAddress.toLetterCellRef(41));
		Assert.assertEquals("AP", ExcelAddress.toLetterCellRef(42));
		Assert.assertEquals("AQ", ExcelAddress.toLetterCellRef(43));
		Assert.assertEquals("AR", ExcelAddress.toLetterCellRef(44));
		Assert.assertEquals("AS", ExcelAddress.toLetterCellRef(45));
		Assert.assertEquals("AT", ExcelAddress.toLetterCellRef(46));
		Assert.assertEquals("AU", ExcelAddress.toLetterCellRef(47));
		Assert.assertEquals("AV", ExcelAddress.toLetterCellRef(48));
		Assert.assertEquals("AW", ExcelAddress.toLetterCellRef(49));
		Assert.assertEquals("AX", ExcelAddress.toLetterCellRef(50));
		Assert.assertEquals("AY", ExcelAddress.toLetterCellRef(51));
		Assert.assertEquals("AZ", ExcelAddress.toLetterCellRef(52));
		Assert.assertEquals("BA", ExcelAddress.toLetterCellRef(53));
		Assert.assertEquals("BB", ExcelAddress.toLetterCellRef(54));
		Assert.assertEquals("FM", ExcelAddress.toLetterCellRef(169));
		Assert.assertEquals("FZ", ExcelAddress.toLetterCellRef(182));
		Assert.assertEquals("FZ", ExcelAddress.toLetterCellRef(182));
		Assert.assertEquals("ZZ", ExcelAddress.toLetterCellRef(702));
		Assert.assertEquals("AAA", ExcelAddress.toLetterCellRef(703));
		Assert.assertEquals("AAB", ExcelAddress.toLetterCellRef(704));
		Assert.assertEquals("AAC", ExcelAddress.toLetterCellRef(705));
		Assert.assertEquals("AAD", ExcelAddress.toLetterCellRef(706));
		Assert.assertEquals("AAE", ExcelAddress.toLetterCellRef(707));
		Assert.assertEquals("AAP", ExcelAddress.toLetterCellRef(718));
		Assert.assertEquals("AAY", ExcelAddress.toLetterCellRef(727));
		Assert.assertEquals("AAZ", ExcelAddress.toLetterCellRef(728));
		Assert.assertEquals("ABA", ExcelAddress.toLetterCellRef(729));
		Assert.assertEquals("ABB", ExcelAddress.toLetterCellRef(730));
		Assert.assertEquals("AGZ", ExcelAddress.toLetterCellRef(884));
		Assert.assertEquals("AZZ", ExcelAddress.toLetterCellRef(1378));
		Assert.assertEquals("BAA", ExcelAddress.toLetterCellRef(1379));
		Assert.assertEquals("BZZ", ExcelAddress.toLetterCellRef(2054));
		//Assert.assertEquals("CAA", ExcelAddress.toLetterCellRef(2055));
		//Assert.assertEquals("CZZ", ExcelAddress.toLetterCellRef(2730));
		//Assert.assertEquals("DKJ", ExcelAddress.toLetterCellRef(3000));
		//Assert.assertEquals("DNL", ExcelAddress.toLetterCellRef(3080));
		//Assert.assertEquals("MOV", ExcelAddress.toLetterCellRef(9200));
		//Assert.assertEquals("XFD", ExcelAddress.toLetterCellRef(16384));

	}

	@Test
	public void fromColumnLettersToOrdinalNumber() {
		Assert.assertEquals(1, ExcelAddress.fromColumnLettersToOrdinalNumber("A"));
		Assert.assertEquals(3, ExcelAddress.fromColumnLettersToOrdinalNumber("C"));
		Assert.assertEquals(26, ExcelAddress.fromColumnLettersToOrdinalNumber("Z"));
		Assert.assertEquals(27, ExcelAddress.fromColumnLettersToOrdinalNumber("AA"));
		Assert.assertEquals(28, ExcelAddress.fromColumnLettersToOrdinalNumber("AB"));
		Assert.assertEquals(29, ExcelAddress.fromColumnLettersToOrdinalNumber("AC"));
		Assert.assertEquals(30, ExcelAddress.fromColumnLettersToOrdinalNumber("AD"));
		Assert.assertEquals(2054, ExcelAddress.fromColumnLettersToOrdinalNumber("BZZ"));
		Assert.assertEquals(704, ExcelAddress.fromColumnLettersToOrdinalNumber("AAB"));
	}

	@Test
	public void advanceRow() {
		ExcelAddress address = new ExcelAddress("A2:I7");
		System.out.println("address = " + address);
		Assert.assertNotNull(address);
		Assert.assertEquals(1, (int) address.getRowStartIndex());
		Assert.assertEquals(0, (int) address.getColumnStartIndex());
		Assert.assertEquals(6, (int) address.getRowEndIndex());
		Assert.assertEquals(8, (int) address.getColumnEndIndex());

		address.advanceRow();
		System.out.println("after move to next row, address = " + address);
		Assert.assertEquals(2, (int) address.getRowStartIndex());
		Assert.assertEquals(0, (int) address.getColumnStartIndex());
		Assert.assertEquals(7, (int) address.getRowEndIndex());
		Assert.assertEquals(8, (int) address.getColumnEndIndex());

		address.advanceRow();
		address.advanceRow();
		address.advanceRow();
		System.out.println("after move up 3 rows, address = " + address);
		Assert.assertEquals(5, (int) address.getRowStartIndex());
		Assert.assertEquals(0, (int) address.getColumnStartIndex());
		Assert.assertEquals(10, (int) address.getRowEndIndex());
		Assert.assertEquals(8, (int) address.getColumnEndIndex());
	}

	@Test
	public void advanceColumn() {
		ExcelAddress address = new ExcelAddress("B15:CS93");
		System.out.println("address = " + address);
		Assert.assertNotNull(address);
		Assert.assertEquals(14, (int) address.getRowStartIndex());
		Assert.assertEquals(1, (int) address.getColumnStartIndex());
		Assert.assertEquals(92, (int) address.getRowEndIndex());
		Assert.assertEquals(96, (int) address.getColumnEndIndex());

		address.advanceColumn();
		System.out.println("after move to next column, address = " + address);
		Assert.assertEquals(14, (int) address.getRowStartIndex());
		Assert.assertEquals(2, (int) address.getColumnStartIndex());
		Assert.assertEquals(92, (int) address.getRowEndIndex());
		Assert.assertEquals(97, (int) address.getColumnEndIndex());

		address.advanceColumn();
		address.advanceColumn();
		System.out.println("after move over 3 columns, address = " + address);
		Assert.assertEquals(14, (int) address.getRowStartIndex());
		Assert.assertEquals(4, (int) address.getColumnStartIndex());
		Assert.assertEquals(92, (int) address.getRowEndIndex());
		Assert.assertEquals(99, (int) address.getColumnEndIndex());
	}
}
