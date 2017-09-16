/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.util;

/**
 * Utility methods for pattern matching. This utility class provides negative pattern matching support
 * which is not provided as part of utility class {@link org.springframework.util.PatternMatchUtils}
 *
 * @author Meherzad Lahewala
 *
 * @since 5.0
 *
 * @see org.springframework.util.PatternMatchUtils
 */
public final class PatternMatchUtils {

	private PatternMatchUtils() { }

	/**
	 * Pattern match against the supplied patterns; also supports negated ('!')
	 * patterns. First match wins (positive or negative).
	 * @param str the string to match.
	 * @param patterns the patterns.
	 * @return true for positive match; false for negative; null if no pattern matches.
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String[], String)
	 */
	public static Boolean smartMatch(String str, String... patterns) {
		if (patterns != null) {
			for (String pattern : patterns) {
				boolean reverse = false;
				String patternToUse = pattern;
				if (pattern.startsWith("!")) {
					reverse = true;
					patternToUse = pattern.substring(1);
				}
				else if (pattern.startsWith("\\")) {
					patternToUse = pattern.substring(1);
				}
				if (org.springframework.util.PatternMatchUtils.simpleMatch(patternToUse, str)) {
					return !reverse;
				}
			}
		}
		return null; //NOSONAR - intentional null return
	}
}
