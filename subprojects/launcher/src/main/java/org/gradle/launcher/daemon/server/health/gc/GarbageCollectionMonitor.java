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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.JavaVersion;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import java.lang.management.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GarbageCollectionMonitor {
    final public static int EVENT_WINDOW = 20;
    final Logger logger = Logging.getLogger(GarbageCollectionMonitor.class);
    final Map<String, SlidingWindow<GarbageCollectionEvent>> events;
    final JVMStrategy jvmStrategy = JVMStrategy.current();
    final ScheduledExecutorService pollingExecutor;

    public GarbageCollectionMonitor(ScheduledExecutorService pollingExecutor) {
        this.pollingExecutor = pollingExecutor;

        List<String> memoryPoolNames = jvmStrategy.getPoolNames();

        ImmutableMap.Builder<String, SlidingWindow<GarbageCollectionEvent>> builder = new ImmutableMap.Builder<String, SlidingWindow<GarbageCollectionEvent>>();
        for (String memoryPool : memoryPoolNames) {
            builder.put(memoryPool, new DefaultSlidingWindow<GarbageCollectionEvent>(EVENT_WINDOW));
        }
        events = builder.build();

        if (!memoryPoolNames.isEmpty()) {
            if (jvmStrategy.isNotificationSupported()) {
                registerGCNotificationListener(memoryPoolNames);
            } else {
                pollForValues(jvmStrategy.getGarbageCollectorName(), jvmStrategy.getPoolNames());
            }
        }
    }

    private void pollForValues(String garbageCollectorName, List<String> memoryPoolNames) {
        pollingExecutor.scheduleAtFixedRate(new GCCheck(memoryPoolNames, garbageCollectorName), 1, 1, TimeUnit.SECONDS);
    }

    private void registerGCNotificationListener(List<String> memoryPoolNames) {
        logger.warn("Registering GC notification listener");

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (memoryPoolNames.contains(pool.getName()) && pool.isCollectionUsageThresholdSupported()) {
                pool.setCollectionUsageThreshold(1);
                logger.warn("Collection usage notification configured for: " + pool.getName());
            } else {
                logger.warn("Collection usage not supported for: " + pool.getName());
            }
        }

        NotificationFilter filter = new GarbageCollectionEventFilter(memoryPoolNames);
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        NotificationEmitter emitter = (NotificationEmitter) memory;
        emitter.addNotificationListener(new GCNotificationListener(events), filter, null);
    }

    public GarbageCollectionStats getTenuredStats() {
        return new GarbageCollectionStats(events.get(jvmStrategy.getTenuredPoolName()).snapshot());
    }

    public GarbageCollectionStats getPermGenStats() {
         if (jvmStrategy.getPermGenPoolName() != null) {
             return new GarbageCollectionStats(events.get(jvmStrategy.getPermGenPoolName()).snapshot());
         } else {
             return null;
         }
    }

    String formatCurrentStats(String poolName) {
        StringBuilder builder = new StringBuilder();
        GarbageCollectionStats stats = new GarbageCollectionStats(events.get(poolName).snapshot());
        builder.append("\n  Current Stats:");
        builder.append("\n    Count: " + stats.getCount());
        builder.append("\n    Rate: " + stats.getRate());
        builder.append("\n    Usage: " + stats.getUsage());
        return builder.toString();
    }

    String formatGCInfo(String memoryPool, GarbageCollectionEvent event) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n  Pool State: " + event.getUsage().toString());
        return builder.toString();
    }

    private class GCCheck implements Runnable {
        private final List<String> memoryPools;
        private final String garbageCollector;

        public GCCheck(List<String> memoryPools, String garbageCollector) {
            this.memoryPools = memoryPools;
            this.garbageCollector = garbageCollector;
        }

        @Override
        public void run() {
            List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
            GarbageCollectorMXBean garbageCollectorMXBean = CollectionUtils.findFirst(garbageCollectorMXBeans, new Spec<GarbageCollectorMXBean>() {
                @Override
                public boolean isSatisfiedBy(GarbageCollectorMXBean mbean) {
                    return mbean.getName().equals(garbageCollector);
                }
            });

            List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
                String pool = memoryPoolMXBean.getName();
                if (memoryPools.contains(pool)) {
                    GarbageCollectionEvent event = new GarbageCollectionEvent(System.currentTimeMillis(), memoryPoolMXBean.getCollectionUsage(), garbageCollectorMXBean.getCollectionCount());
                    events.get(pool).slideAndInsert(event);
                    logger.warn("Garbage Collection Check (" + pool + "): " + formatCurrentStats(pool) + formatGCInfo(pool, event));
                }
            }
        }
    }

    private class GCNotificationListener implements NotificationListener {
        final Map<String, SlidingWindow<GarbageCollectionEvent>> events;

        public GCNotificationListener(Map<String, SlidingWindow<GarbageCollectionEvent>> events) {
            this.events = events;
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification.getType().equals(MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED)) {
                MemoryNotificationInfo info = MemoryNotificationInfo.from((CompositeData) notification.getUserData());
                String memoryPoolName = info.getPoolName();
                if (events.containsKey(memoryPoolName)) {
                    GarbageCollectionEvent event = new GarbageCollectionEvent(notification.getTimeStamp(), info.getUsage());
                    events.get(memoryPoolName).slideAndInsert(event);
                    logger.warn("Garbage Collection Event (" + memoryPoolName + "): " + formatCurrentStats(memoryPoolName) + formatGCInfo(memoryPoolName, event));
                }
            }
        }
    }

    public enum JVMStrategy {
        IBM("Java heap", null, "MarkSweepCompact", false),
        SUN_HOTSPOT_JDK7_OR_EARLIER("PS Old Gen", "PS Perm Gen", "PS MarkSweep", false),
        SUN_HOTSPOT_JDK8_OR_LATER("PS Old Gen", null, "PS MarkSweep", false),
        UNSUPPORTED(null, null, null, false);

        final String tenuredPoolName;
        final String permGenPoolName;
        final String garbageCollectorName;
        final boolean notificationSupported;

        JVMStrategy(String tenuredPoolName, String permGenPoolName, String garbageCollectorName, boolean notificationSupported) {
            this.tenuredPoolName = tenuredPoolName;
            this.permGenPoolName = permGenPoolName;
            this.garbageCollectorName = garbageCollectorName;
            this.notificationSupported = notificationSupported;
        }

        static JVMStrategy current() {
            String vendor = System.getProperty("java.vm.vendor");

            if (vendor.equals("IBM Corporation")) {
                return IBM;
            }

            if (vendor.equals("Oracle Corporation")) {
                if (JavaVersion.current().isJava8Compatible()) {
                    return SUN_HOTSPOT_JDK8_OR_LATER;
                } else {
                    return SUN_HOTSPOT_JDK7_OR_EARLIER;
                }
            }

            return UNSUPPORTED;
        }

        public List<String> getPoolNames() {
            ImmutableList.Builder<String> builder = ImmutableList.builder();

            if (permGenPoolName != null) {
                builder.add(permGenPoolName);
            }

            if (tenuredPoolName != null) {
                builder.add(tenuredPoolName);
            }

            return builder.build();
        }

        public String getTenuredPoolName() {
            return tenuredPoolName;
        }

        public String getPermGenPoolName() {
            return permGenPoolName;
        }

        public String getGarbageCollectorName() {
            return garbageCollectorName;
        }

        public boolean isNotificationSupported() {
            return notificationSupported;
        }
    }
}
