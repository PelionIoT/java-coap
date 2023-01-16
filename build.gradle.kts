import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.spotbugs.snom.Effort

plugins {
    id("java")
    id("maven-publish")
    id("com.github.mfarsikov.kewt-versioning") version "1.0.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.18"
    id("com.github.ben-manes.versions") version "0.44.0"
    id("pmd")
    id("com.github.spotbugs") version "5.0.13"
    id("org.gradle.signing")
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("com.adarshr.test-logger") version "3.2.0"
}

allprojects {
    apply {
        plugin("com.github.mfarsikov.kewt-versioning")
        plugin("se.patrikerdes.use-latest-versions")
        plugin("com.github.ben-manes.versions")
    }

    repositories {
        mavenCentral()
    }

    kewtVersioning.configuration {
        separator = ""
    }
    version = kewtVersioning.version
    group = "io.github.open-coap"

    tasks.withType<DependencyUpdatesTask> {
        rejectVersionIf {
            // newer version of logback-classic is not java8 compatible
            candidate.module == "logback-classic"
        }
    }

}

subprojects {
    apply {
        plugin("java")
        plugin("pmd")
        plugin("com.github.spotbugs")
        plugin("jacoco")
        plugin("maven-publish")
        plugin("org.gradle.signing")
        plugin("com.adarshr.test-logger")
    }

    val projSourceSets = extensions.getByName("sourceSets") as SourceSetContainer

    tasks {
        withType<Test> {
            useJUnitPlatform {
                excludeTags("Benchmark")
            }
        }

        withType<JavaCompile> {
            sourceCompatibility = "1.8"
            targetCompatibility = "1.8"
            options.encoding = "UTF-8"
        }

        withType<JacocoReport> {
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        create<Jar>("sourceJar") {
            archiveClassifier.set("sources")
            from(projSourceSets["main"].allSource)
        }

        create<Jar>("javadocJar") {
            archiveClassifier.set("javadoc")
            from(javadoc)
        }

        named("pmdTest").get().enabled = false
        named("spotbugsTest").get().enabled = false
    }

    pmd {
        toolVersion = "6.53.0"
        isConsoleOutput = true
        ruleSets = emptyList()
        ruleSetFiles = files(rootProject.file("pmd-rules.xml"))
    }

    spotbugs {
        effort.set(Effort.MAX)
        excludeFilter.set(rootProject.file("spotbugs-exlude.xml"))
    }

    publishing {
        publications {
            create<MavenPublication>("OSSRH") {
                from(components["java"])
                groupId = "io.github.open-coap"
                artifact(tasks["sourceJar"])
                artifact(tasks["javadocJar"])

                pom {
                    name.set("Java CoAP")
                    description.set("Java implementation of CoAP protocol")
                    url.set("https://github.com/open-coap/java-coap")
                    scm {
                        url.set("https://github.com/open-coap/java-coap")
                    }
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            name.set("Szymon Sasin")
                            email.set("szymon.sasin@gmail.com")
                        }
                    }
                }
            }
        }
    }

    signing {
        val signingKeyId: String? by project
        val signingKey: String? by project
        val signingPassword: String? by project

        if (signingKey != null) {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
            sign(publishing.publications["OSSRH"])
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            val ossrhUserName: String? by project
            val ossrhPassword: String? by project

            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(ossrhUserName)
            password.set(ossrhPassword)
        }
    }
}
