plugins {
    id 'com.github.open-coap.java-conventions'
}

description = 'lwm2m'


dependencies {
    api 'com.google.code.gson:gson:2.9.1'
    api 'org.slf4j:slf4j-api:2.0.0'

    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.9.0'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.9.0'
    testImplementation 'commons-io:commons-io:2.11.0'
    testImplementation 'ch.qos.logback:logback-classic:1.4.0'
    testImplementation 'org.mockito:mockito-core:4.7.0'
    testImplementation 'org.hamcrest:hamcrest-all:1.3'
    testImplementation 'nl.jqno.equalsverifier:equalsverifier:3.10.1'
}
