// the bridge to a module that brings its own code in a zip: mochy and the module both build it, but only its shared package crosses at runtime
plugins {
    id("kotlin")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":modules:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(libs.junit)
}
