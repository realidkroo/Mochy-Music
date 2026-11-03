@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
}

// f-droid foojay workaround
// plugins {
//     id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
// }

rootProject.name = "mochy"
include(":app")
include(":providers:canvas")
include(":providers:innertube")
include(":providers:kugou")
include(":providers:lrclib")
include(":providers:lastfm")
include(":providers:betterlyrics")
include(":providers:simpmusic")
include(":providers:youlyplus")
include(":providers:shazamkit")
include(":providers:artistvideo")
include(":providers:applecanvas")
include(":providers:paxsenixlyrics")
include(":providers:directlyrics")
include(":providers:musixmatch")
include(":providers:genius")
include(":providers:wikipedia")

// source modules built into mochy, the api every module is written against, and the bridge to a module that brings its own code in a zip
include(":modules:api")
include(":modules:bridge")
include(":modules:applemusic")
include(":modules:monochrome")
include(":modules:jiosaavn")


// local newpipe extractor
//includeBuild("../NewPipeExtractor") {
//    dependencySubstitution {
//        substitute(module("com.github.teamnewpipe:NewPipeExtractor")).using(project(":extractor"))
//    }
//}
