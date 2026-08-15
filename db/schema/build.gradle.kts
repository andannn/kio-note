plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm()
    linuxX64()

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kio.poller.select)
        }
        linuxMain.dependencies {
            implementation(libs.kio.poller.uring)
        }
        commonMain.dependencies {
            api(libs.kio.postgres.migration)
        }
    }
}
