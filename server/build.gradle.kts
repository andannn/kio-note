plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("kio.note.ApplicationKt")
        }
    }

    sourceSets {
        compilerOptions {
            freeCompilerArgs.set(listOf("-Xcontext-parameters"))
        }
        jvmMain.dependencies {
            implementation(libs.kio.poller.select)
        }
        commonMain.dependencies {
            implementation(libs.kio.http)
            implementation(libs.kio.postgres.connection)
            implementation(libs.kio.io)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}