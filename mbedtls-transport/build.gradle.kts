plugins {
    id("java-library")
}

description = "mbedtls-transport"

dependencies {
    api(project(":coap-core"))
    api("com.github.open-coap:kotlin-mbedtls:1.6.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    testImplementation("ch.qos.logback:logback-classic:1.3.5")
}
