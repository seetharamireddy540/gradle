/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetaData;
import org.gradle.internal.component.external.model.ModuleDescriptorState;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.component.model.ModuleSource;

import java.math.BigInteger;

class MavenModuleCacheEntry extends ModuleDescriptorCacheEntry {
    final String snapshotTimestamp;
    final String packaging;

    public MavenModuleCacheEntry(boolean isChanging, String packaging, String snapshotTimestamp, long createTimestamp, BigInteger moduleDescriptorHash, ModuleSource moduleSource) {
        super(TYPE_MAVEN, isChanging, createTimestamp, moduleDescriptorHash, moduleSource);
        this.packaging = packaging;
        this.snapshotTimestamp = snapshotTimestamp;
    }

    public MutableModuleComponentResolveMetaData createMetaData(ModuleComponentIdentifier componentIdentifier, ModuleDescriptorState descriptor) {
        // TODO Relocation is not currently cached
        return configure(new DefaultMavenModuleResolveMetaData(componentIdentifier, descriptor, packaging, false));
    }
}
