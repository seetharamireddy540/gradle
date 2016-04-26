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

import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.openmbean.CompositeData;

public class GarbageCollectionEventFilter extends NotificationFilterSupport {
    final String name;
    final String action;

    public GarbageCollectionEventFilter(String name, String action) {
        this.name = name;
        this.action = action;
        this.enableType(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION);
    }

    @Override
    public synchronized boolean isNotificationEnabled(Notification notification) {
        return super.isNotificationEnabled(notification) && matches(notification);
    }

    private boolean matches(Notification notification) {
        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
        return info.getGcName().equals(name) && info.getGcAction().equals(action);
    }
}
