plugins {
    id("java-library")
}

description = "mbedtls-transport"

dependencies {
    api(project(":coap-core"))
    api("com.github.open-coap:kotlin-mbedtls:v1.5.4")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    testImplementation("ch.qos.logback:logback-classic:1.3.0")
}