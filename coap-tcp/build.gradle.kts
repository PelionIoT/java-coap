plugins {
    id("java-library")
}

description = "coap-tcp"

dependencies {
    api(project(":coap-core"))
    api("org.slf4j:slf4j-api:2.0.5")

    testImplementation(testFixtures(project(":coap-core")))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    testImplementation("ch.qos.logback:logback-classic:1.3.0")
    testImplementation("org.mockito:mockito-core:4.9.0")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("nl.jqno.equalsverifier:equalsverifier:3.11.1")
    testImplementation("org.awaitility:awaitility:4.2.0")
}
