plugins {
    id("java-library")
}

description = "coap-core"

dependencies {
    api("org.slf4j:slf4j-api:2.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    testImplementation("ch.qos.logback:logback-classic:1.3.0")
    testImplementation("org.mockito:mockito-core:4.7.0")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("nl.jqno.equalsverifier:equalsverifier:3.10.1")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testImplementation("io.github.artsok:rerunner-jupiter:2.1.6")
}
