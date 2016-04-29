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



package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.launcher.daemon.server.health.DaemonStatus
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

class DaemonPerformanceMonitoringIntegrationTest extends DaemonIntegrationSpec {
    int maxBuilds
    String heapSize
    int leakRate
    Closure leakyBuildScript

    def setup() {
        leakyBuildScript = OLD_GEN_LEAK
    }

    @Unroll
    def "when build leaks quickly daemon is expired eagerly (#heap heap)"() {
        when:
        maxBuilds = builds
        heapSize = heap
        leakRate = rate

        then:
        daemonIsExpiredEagerly()

        where:
        builds | heap    | rate
        10     | "200m"  | 4000
        10     | "1024m" | 15000
    }

    @Unroll
    def "when build leaks slowly daemon is eventually expired (#heap heap)"() {
        when:
        maxBuilds = builds
        heapSize = heap
        leakRate = rate

        then:
        daemonIsExpiredEagerly()

        where:
        builds | heap    | rate
        40     | "200m"  | 800
        40     | "1024m" | 4000
    }

    def "when build leaks within available memory the daemon is not expired"() {
        when:
        maxBuilds = 20
        heapSize = "500m"
        leakRate = 300

        then:
        !daemonIsExpiredEagerly()
    }

    @Requires(TestPrecondition.JDK7_OR_EARLIER)
    def "when build leaks permgen space daemon is expired"() {
        when:
        leakyBuildScript = PERM_GEN_LEAK
        maxBuilds = 20
        heapSize = "200m"
        leakRate = 3500

        then:
        daemonIsExpiredEagerly()
    }

    private boolean daemonIsExpiredEagerly() {
        def dataFile = file("stats")
        setupLeakyBuild()
        int newDaemons = 0
        try {
            for (int i = 0; i < maxBuilds; i++) {
                executer.noExtraLogging()
                executer.withBuildJvmOpts("-D${DaemonStatus.EXPIRE_AT_PROPERTY}=80", "-Xmx${heapSize}", "-Dorg.gradle.daemon.performance.logging=true")
                def r = run()
                if (r.output.contains("Starting build in new daemon [memory: ")) {
                    newDaemons++;
                }
                if (newDaemons > 1) {
                    return true
                }
                def lines = r.output.readLines()
                dataFile << lines[lines.findLastIndexOf { it.startsWith "Starting" }]
                dataFile << "  " + lines[lines.findLastIndexOf { it.contains "Total time:" }]
                dataFile << "\n"
            }
        } finally {
            println dataFile.text
        }
        return false
    }

    private void setupLeakyBuild() {
        buildFile << leakyBuildScript()
    }

    private final Closure OLD_GEN_LEAK = { """
        class State {
            static int x
            static map = [:]
        }
        State.x++

        //simulate normal collectible objects
        3000.times {
            State.map.put(it, "foo" * ${leakRate})
        }

        //simulate the leak
        1000.times {
            State.map.put(UUID.randomUUID(), "foo" * ${leakRate})
        }

        println "Build: " + State.x
    """ }

    private final Closure PERM_GEN_LEAK = {
        leakRate.times {
            file("buildSrc/src/main/java/Generated${it}.java") << """
                public class Generated${it} { }
            """
        }

        return """
            import java.net.URLClassLoader

            class State {
                static int x
                static map = [:]
            }
            State.x++

            //simulate normal collectible objects
            4000.times {
                State.map.put(it, "foo" * 3000)
            }

            //simul
            5.times {
                ClassLoader classLoader1 = new URLClassLoader(buildscript.classLoader.URLs)
                ${leakRate}.times {
                    classLoader1.loadClass("Generated\${it}")
                }
                State.map.put("CL${it}", classLoader1)
            }

            //simulate the leak
            ClassLoader classLoader2 = new URLClassLoader(buildscript.classLoader.URLs)
            ${leakRate}.times {
                classLoader2.loadClass("Generated\${it}")
            }
            State.map.put(UUID.randomUUID(), classLoader2)

            println "Build: " + State.x
        """
    }

}
