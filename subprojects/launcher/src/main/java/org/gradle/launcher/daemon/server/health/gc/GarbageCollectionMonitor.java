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

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import org.gradle.api.JavaVersion;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.management.InstanceNotFoundException;
import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Map;

public class GarbageCollectionMonitor {
    final public static int EVENT_WINDOW = 20;
    final Logger logger = Logging.getLogger(GarbageCollectionMonitor.class);
    final SlidingWindow<GcInfo> events = new DefaultSlidingWindow<GcInfo>(EVENT_WINDOW);

    public GarbageCollectionMonitor() {
        registerGCNotificationListener();
    }

    private void registerGCNotificationListener() {
        logger.warn("Registering GC notification listener");
        NotificationFilterSupport filter = new GarbageCollectionEventFilter("PS MarkSweep", "end of major GC");
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            try {
                ManagementFactory.getPlatformMBeanServer().addNotificationListener(gc.getObjectName(), new GCNotificationListener(events), filter, null);
            } catch (InstanceNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public GarbageCollectionStats getOldGenStats() {
        return new GarbageCollectionStats("PS Old Gen", events.snapshot());
    }

    public GarbageCollectionStats getPermGenStats() {
         if (!JavaVersion.current().isJava8Compatible()) {
             return new GarbageCollectionStats("PS Perm Gen", events.snapshot());
         } else {
             return null;
         }
    }

    private class GCNotificationListener implements NotificationListener {
        final SlidingWindow<GcInfo> events;

        public GCNotificationListener(SlidingWindow<GcInfo> events) {
            this.events = events;
        }

        @Override
        public void handleNotification(Notification notification, Object handback) {
            if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
                events.slideAndInsert(info.getGcInfo());
                logger.warn("Garbage Collection Event: " + formatCurrentStats() + formatGCInfo(info));
            }
        }

        String formatCurrentStats() {
            StringBuilder builder = new StringBuilder();
            GarbageCollectionStats stats = getOldGenStats();
            builder.append("\n  Current Stats:");
            builder.append("\n    Count: " + stats.getCount());
            builder.append("\n    Rate: " + stats.getRate());
            builder.append("\n    Usage: " + stats.getUsage());
            return builder.toString();
        }

        String formatGCInfo(GarbageCollectionNotificationInfo info) {
            StringBuilder builder = new StringBuilder();
            builder.append("\n  " + info.getGcName() + ":" + info.getGcAction() + ":" + info.getGcCause());
            GcInfo gcInfo = info.getGcInfo();
            builder.append("\n    id: " + gcInfo.getId());
            builder.append("\n    duration: " + gcInfo.getDuration());
            Map<String, MemoryUsage> beforeGc = gcInfo.getMemoryUsageBeforeGc();
            Map<String, MemoryUsage> afterGc = gcInfo.getMemoryUsageAfterGc();
            for (String pool : new String[] { "PS Old Gen", "PS Perm Gen" }) {
                if (beforeGc.containsKey(pool)) {
                    builder.append("\n    " + pool);
                    builder.append("\n      before: " + beforeGc.get(pool).toString());
                    builder.append("\n       after: " + afterGc.get(pool).toString());
                }
            }
            return builder.toString();
        }
    }
}
