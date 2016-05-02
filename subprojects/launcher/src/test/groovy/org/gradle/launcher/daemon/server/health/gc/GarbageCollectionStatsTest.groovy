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

package org.gradle.launcher.daemon.server.health.gc

import spock.lang.Specification

import java.lang.management.MemoryUsage;

public class GarbageCollectionStatsTest extends Specification {
    def "correctly calculates garbage collection rate with events"() {
        expect:
        new GarbageCollectionStats(eventStream).rate == 1
    }

    def "correctly calculates average usage with events"() {
        expect:
        new GarbageCollectionStats(eventStream).usage == 60
    }

    def "correctly calculates garbage collection rate with checks"() {
        expect:
        new GarbageCollectionStats(checkStream).rate == 3
    }

    def "correctly calculates average usage with checks"() {
        expect:
        new GarbageCollectionStats(checkStream).usage == 71
    }

    Set<GarbageCollectionEvent> getEventStream() {
        Set<GarbageCollectionEvent> events = [
            new GarbageCollectionEvent(1000, new MemoryUsage(0, 250, 1000, 1000)),
            new GarbageCollectionEvent(2000, new MemoryUsage(0, 500, 1000, 1000)),
            new GarbageCollectionEvent(3000, new MemoryUsage(0, 750, 1000, 1000)),
            new GarbageCollectionEvent(4000, new MemoryUsage(0, 900, 1000, 1000))
        ]
        return events
    }

    Set<GarbageCollectionEvent> getCheckStream() {
        Set<GarbageCollectionEvent> checks = [
            new GarbageCollectionEvent(1000, new MemoryUsage(0, 250, 1000, 1000), 1),
            new GarbageCollectionEvent(2000, new MemoryUsage(0, 500, 1000, 1000), 3),
            new GarbageCollectionEvent(2500, new MemoryUsage(0, 500, 1000, 1000), 3),
            new GarbageCollectionEvent(3000, new MemoryUsage(0, 750, 1000, 1000), 6),
            new GarbageCollectionEvent(3500, new MemoryUsage(0, 750, 1000, 1000), 6),
            new GarbageCollectionEvent(4000, new MemoryUsage(0, 900, 1000, 1000), 10)
        ]
    }
}
