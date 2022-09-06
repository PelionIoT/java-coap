plugins {
    id 'se.patrikerdes.use-latest-versions' version '0.2.18'
    id 'com.github.ben-manes.versions' version '0.42.0'
}

allprojects {
    apply plugin: 'se.patrikerdes.use-latest-versions'
    apply plugin: 'com.github.ben-manes.versions'

    tasks.named("dependencyUpdates").configure {
        rejectVersionIf {
            // newer version of logback-classic is not java8 compatible
            it.candidate.module == 'logback-classic'
        }
    }

}
