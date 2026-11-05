plugins {
    id("com.google.protobuf") version "0.10.0"
}

dependencies {
    api("me.bechberger:ap-loader-all:4.6-14")
    api("com.google.protobuf:protobuf-java:4.30.1")

    compileOnly("org.jspecify:jspecify:1.0.0")
    compileOnly("it.unimi.dsi:fastutil:8.5.18")
    compileOnly("com.google.code.gson:gson:2.13.2")
    compileOnly("org.yaml:snakeyaml:2.3")
    compileOnly("org.apache.logging.log4j:log4j-api:2.26.0")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        proto {
            srcDir(rootProject.file("amethyst/proto"))
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.30.1"
    }
}
