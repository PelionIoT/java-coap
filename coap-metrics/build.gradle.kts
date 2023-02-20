plugins {
    id("java-library")
}

description = "coap-metrics"

dependencies {
    api(project(":coap-core"))

    implementation("io.micrometer:micrometer-core:1.10.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}
