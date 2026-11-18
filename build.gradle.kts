import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm

// AGP owns Kotlin integration. Pin its compiler so JVM 25 remains supported.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        constraints {
            add("classpath", "com.google.guava:guava:33.8.0-jre") {
                because("Host deserialization and hash-flooding fixes")
            }
            add("classpath", "org.apache.commons:commons-lang3:3.22.0")
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") {
                because("CVE-2024-29371")
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("CVE-2021-33813")
            }
            for (module in listOf("bcprov", "bcpkix", "bcutil")) {
                add("classpath", "org.bouncycastle:$module-jdk18on:1.87") {
                    because("Host cryptography and certificate validation fixes")
                }
            }
            add("classpath", "org.apache.httpcomponents:httpclient:4.5.14")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    toolchainDownloadUrls.empty()
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
