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

package org.nexial.core.reports;

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ExternalArtifact implements Serializable {
	private String id;
	private TestCaseType type;
	// analogous to testset or testsuite
	private ExternalArtifact parent;

	/** different type (aka repository) of test case management systems. */
	public enum TestCaseType {
		Rally, Zephyr, Unknown
	}

	public static ExternalArtifact newRallyTestCase(String testcaseId) {
		ExternalArtifact tc = new ExternalArtifact();
		tc.setId(testcaseId);
		tc.setType(TestCaseType.Rally);
		return tc;
	}

	public static ExternalArtifact newZephyrTestCase(String testcaseId) {
		ExternalArtifact tc = new ExternalArtifact();
		tc.setId(testcaseId);
		tc.setType(TestCaseType.Zephyr);
		return tc;
	}

	public static ExternalArtifact newUnknownTestCase() {
		ExternalArtifact tc = new ExternalArtifact();
		tc.setType(TestCaseType.Unknown);
		return tc;
	}

	public String getId() { return id; }

	public void setId(String id) { this.id = id; }

	public TestCaseType getType() { return type; }

	public void setType(TestCaseType type) { this.type = type; }

	public ExternalArtifact getParent() { return parent; }

	public void setParent(ExternalArtifact parent) { this.parent = parent; }

	@Override
	public int hashCode() { return new HashCodeBuilder().append(this.id).append(this.type).toHashCode(); }

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {return false;}
		if (getClass() != obj.getClass()) {return false;}
		final ExternalArtifact other = (ExternalArtifact) obj;
		return new EqualsBuilder().append(this.id, other.id).append(this.type, other.type).isEquals();
	}

	public boolean isTypeUnknown() { return type == TestCaseType.Unknown; }

	@Override
	public String toString() { return "ExternalArtifact{id='" + id + "', type=" + type + "}"; }
}
