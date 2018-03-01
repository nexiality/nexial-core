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

package org.nexial.core.variable;

import org.apache.commons.lang3.StringUtils;

public class Count {
	public static final Matcher UPPER = TypedMatcher.newUpperMatcher();
	public static final Matcher LOWER = TypedMatcher.newLowerMatcher();
	public static final Matcher NUMBER = TypedMatcher.newNumericMatcher();
	public static final Matcher ALPHANUMERIC = TypedMatcher.newAlphanumericMatcher();
	public static final Matcher PUNCTUATION = TypedMatcher.newPunctuationMatcher();
	public static final Matcher WHITESPACE = new WhitespaceMatcher();

	interface Matcher {
		boolean match(char ch);
	}

	static class WhitespaceMatcher implements Matcher {
		@Override
		public boolean match(char ch) { return Character.isWhitespace(ch); }
	}

	static class TypedMatcher implements Matcher {
		private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
		private static final String NUMBER = "0123456789";
		private static final String ALPHANUMERIC = NUMBER + UPPER + LOWER;
		private static final String PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

		private String matchBy;

		TypedMatcher(String matchBy) { this.matchBy = matchBy; }

		@Override
		public boolean match(char ch) { return StringUtils.contains(matchBy, ch); }

		static TypedMatcher newPunctuationMatcher() { return new TypedMatcher(PUNCTUATION); }

		static TypedMatcher newUpperMatcher() { return new TypedMatcher(UPPER); }

		static TypedMatcher newLowerMatcher() { return new TypedMatcher(LOWER); }

		static TypedMatcher newNumericMatcher() { return new TypedMatcher(NUMBER); }

		static TypedMatcher newAlphanumericMatcher() { return new TypedMatcher(ALPHANUMERIC); }
	}

	static class NegateMatcher implements Matcher {
		private String omitChars;

		NegateMatcher(String omitChars) { this.omitChars = omitChars; }

		@Override
		public boolean match(char ch) { return !StringUtils.contains(omitChars, ch); }
	}

	public Count() { init(); }

	public String upper(String text) { return count(text, UPPER); }

	public String lower(String text) { return count(text, LOWER); }

	public String number(String text) { return count(text, NUMBER); }

	public String alphanumeric(String text) { return count(text, ALPHANUMERIC); }

	public String whitespace(String text) { return count(text, WHITESPACE); }

	public String punctuation(String text) { return count(text, PUNCTUATION); }

	public String any(String text, String any) { return count(text, new TypedMatcher(any)); }

	public String size(String text) { return StringUtils.length(text) + ""; }

	public String omit(String text, String omitChars) { return count(text, new NegateMatcher(omitChars)); }

	public String sequence(String text, String sequence) {
		if (StringUtils.isEmpty(text)) { return "0"; }
		if (StringUtils.isEmpty(sequence)) { return "0"; }
		return StringUtils.countMatches(text, sequence) + "";
	}

	protected void init() { }

	private String count(String text, Matcher matcher) {
		if (StringUtils.isEmpty(text)) { return "0"; }
		if (matcher == null) { return "0"; }

		int matched = 0;
		char[] chars = text.toCharArray();
		for (char ch : chars) { if (matcher.match(ch)) { matched++; } }

		return matched + "";
	}
}
