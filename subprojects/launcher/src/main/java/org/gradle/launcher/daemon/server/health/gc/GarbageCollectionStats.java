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

package org.gradle.launcher.daemon.server.health.gc;

import com.sun.management.GcInfo;
import org.gradle.internal.util.NumberUtil;

import java.lang.management.MemoryUsage;
import java.util.Set;

public class GarbageCollectionStats {
    final private String poolName;
    final private double slope;
    final private double rate;
    final private long used;
    final private long max;
    final private int count;

    public GarbageCollectionStats(String poolName, Set<GcInfo> events) {
        this.poolName = poolName;
        this.slope = calculateSlope(poolName, events);
        this.rate = calculateRate(events);
        this.used = calculateAverageUsage(poolName, events);
        this.max = calculateMaxSize(poolName, events);
        this.count = events.size();
    }

    static double calculateSlope(String poolName, Set<GcInfo> events) {
        if (events.size() < 2) {
            return Double.NaN;
        }

        double sumXX = 0;
        double sumXY = 0;
        int y = 0;
        for (GcInfo event : events) {
            MemoryUsage afterUsage = event.getMemoryUsageAfterGc().get(poolName);
            long remaining = afterUsage.getMax() - afterUsage.getUsed();
            sumXX += remaining * remaining;
            sumXY += remaining * y;
            y++;
        }

        return sumXY / sumXX;
    }

    static double calculateRate(Set<GcInfo> events) {
        if (events.size() < 2) {
            return Double.NaN;
        }

        long firstGC = 0;
        long lastGC = 0;
        for (GcInfo event : events) {
            if (firstGC == 0) {
                firstGC = event.getStartTime();
            } else {
                lastGC = event.getStartTime();
            }
        }
        long elapsed = lastGC - firstGC;
        return ((double)events.size()) / elapsed * 1000;
    }

    static long calculateAverageUsage(String poolName, Set<GcInfo> events) {
        if (events.size() < 1) {
            return 0;
        }

        long total = 0;
        for (GcInfo event : events) {
            MemoryUsage usage = event.getMemoryUsageAfterGc().get(poolName);
            total += usage.getUsed();
        }
        return total / events.size();
    }

    static long calculateMaxSize(String poolName, Set<GcInfo> events) {
        if (events.size() < 2) {
            return 0;
        }

        // Maximum pool size is fixed, so we should only need to get it from the first event
        MemoryUsage usage = events.iterator().next().getMemoryUsageAfterGc().get(poolName);
        return usage.getMax();
    }

    public String getPoolName() {
        return poolName;
    }

    public double getSlope() {
        return slope;
    }

    public double getRate() {
        return rate;
    }

    public int getUsage() {
        return NumberUtil.percentOf(used, max);
    }

    public double getUsed() {
        return used;
    }

    public long getMax() {
        return max;
    }

    public int getCount() {
        return count;
    }
}
