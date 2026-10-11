plugins {
    java
}

repositories {
    maven("https://repo.essentialsx.net/releases/")
}

dependencies {
    compileOnly("net.essentialsx:EssentialsX:2.21.2") {
        isTransitive = false
    }
}
