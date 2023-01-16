plugins {
    id("application")
}

description = "example-client"

dependencies {
    implementation(project(":coap-core"))
    implementation(project(":coap-tcp"))
    implementation(project(":lwm2m"))
    implementation(project(":coap-mbedtls"))
    implementation("org.slf4j:slf4j-api:2.0.6")
    implementation("ch.qos.logback:logback-classic:1.3.5")

    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    testImplementation("org.awaitility:awaitility:4.2.0")
}

tasks {
    create<JavaExec>("runDeviceEmulator") {
        mainClass.set("com.mbed.coap.cli.DeviceEmulator")
        classpath = sourceSets["main"].runtimeClasspath
    }

    withType<JacocoBase> { enabled = false }
    withType<AbstractPublishToMaven> { enabled = false }
}

application {
    mainClass.set("com.mbed.coap.cli.CoapCli")
}

distributions {
    application.applicationName = "coap"
    main {
        distributionBaseName.set("coap-cli")
    }
}
