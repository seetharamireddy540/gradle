/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.internal.component.model.DefaultDependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultProjectDependencyMetaData extends DefaultDependencyMetaData {
    private final String projectPath;

    public DefaultProjectDependencyMetaData(String projectPath, ModuleVersionSelector requested, Map<String, List<String>> confs,
                                            Map<IvyArtifactName, Set<String>> dependencyArtifacts, Map<ExcludeRule, Set<String>> excludeRules,
                                            String dynamicConstraintVersion, boolean changing, boolean transitive) {
        super(requested, confs, dependencyArtifacts, excludeRules, dynamicConstraintVersion, changing, transitive);
        this.projectPath = projectPath;
    }

    @Override
    public ProjectComponentSelector getSelector() {
        return new DefaultProjectComponentSelector(projectPath);
    }
}
