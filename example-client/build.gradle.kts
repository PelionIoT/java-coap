plugins {
    id 'application'
}

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

description = 'example-client'

compileJava {
    sourceCompatibility = '1.8'
    targetCompatibility = '1.8'
}

dependencies {
    implementation project(':coap-core')
    implementation project(':lwm2m')
    implementation project(':mbedtls-transport')
    implementation 'org.slf4j:slf4j-api:2.0.0'
    implementation 'ch.qos.logback:logback-classic:1.3.0'

    testImplementation 'org.mockito:mockito-core:4.7.0'
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.9.0'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.9.0'
    testImplementation 'org.awaitility:awaitility:4.2.0'
}

application {
    mainClassName = 'com.mbed.coap.cli.CoapCli'
}

test {
    useJUnitPlatform()
}

task(runDeviceEmulator, dependsOn: 'classes', type: JavaExec) {
    main = 'com.mbed.coap.cli.DeviceEmulator'
    classpath = sourceSets.main.runtimeClasspath
}


distributions {
    applicationName = 'coap'
    main {
        distributionBaseName = 'coap-cli'
    }
}
