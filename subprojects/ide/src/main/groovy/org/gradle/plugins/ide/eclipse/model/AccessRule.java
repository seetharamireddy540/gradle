/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;

/**
 * Access rule associated to a classpath entry.
 */
public class AccessRule {
    public String kind;
    public String pattern;

    public AccessRule(String kind, String pattern) {
        assert kind != null && pattern != null;
        this.kind = kind;
        this.pattern = pattern;
    }

    public String getKind() {
        return kind;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AccessRule that = (AccessRule) o;
        return Objects.equal(kind, that.kind) && Objects.equal(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(kind, pattern);
    }

    @Override
    public String toString() {
        return "AccessRule{kind='" + kind + "', pattern='" + pattern +  "'}";
    }
}

