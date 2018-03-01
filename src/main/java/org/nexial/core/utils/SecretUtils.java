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

package org.nexial.core.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import static org.nexial.core.NexialConst.DEF_CHARSET;

public final class SecretUtils {
	private SecretUtils() { }

	public static String unscramble(String scram) throws UnsupportedEncodingException {
		if (scram == null) {return null; }
		String stage1 = StringUtils.reverse(scram);
		String stage2 = URLDecoder.decode(stage1, DEF_CHARSET);
		byte[] stage3 = Base64.decodeBase64(stage2.getBytes());
		return new String(stage3, DEF_CHARSET);
	}

	public static String scramble(String scram) throws UnsupportedEncodingException {
		if (scram == null) { return null; }
		byte[] stage1 = Base64.encodeBase64(scram.getBytes());
		String stage2 = URLEncoder.encode(new String(stage1, DEF_CHARSET), DEF_CHARSET);
		return StringUtils.reverse(stage2);
	}

	/*
	public static void main(String[] args) throws Exception {
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		String plaintext;
		String scram;
		boolean scramming = false;

		if (scramming) {
			System.out.print("Plain text:  ");
			plaintext = scanner.nextLine();
			scram = scramble(plaintext);
			System.out.println("Scrambled: " + scram);
		} else {
			System.out.print("Scrambled: ");
			scram = scanner.nextLine();
			plaintext = unscramble(scram);
			System.out.println("Plain text:  " + plaintext);
		}
	}
	*/
}
