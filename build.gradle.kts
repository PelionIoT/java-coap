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

        named("pmdTest").get().enabled = false
        named("spotbugsTest").get().enabled = false
    }

    pmd {
        toolVersion = "5.6.1"
        isConsoleOutput = true
        sourceSets = listOf(projSourceSets["main"])
        incrementalAnalysis.set(false)
        ruleSets = emptyList()
        ruleSetFiles = files(rootProject.file("pmd-rules.xml"))
    }

    spotbugs {
        effort.set(Effort.MAX)
        excludeFilter.set(rootProject.file("spotbugs-exlude.xml"))
    }

    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/" + System.getenv("GITHUB_REPOSITORY"))
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
        publications {
            create<MavenPublication>("gpr") {
                groupId = "com.github.open-coap.java-coap"
                artifact(tasks["sourceJar"])
                from(components["java"])

                pom {
                    name.set("Java CoAP")
                    description.set("Java implementation of CoAP protocol")
                    url.set("https://github.com/open-coap/java-coap")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
    }
}
