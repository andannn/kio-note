plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("kio.note.ApplicationKt")
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kio.poller.select)
        }
        commonMain.dependencies {
            implementation(libs.kio.http)
            implementation(libs.kio.io)
        }
    }
}