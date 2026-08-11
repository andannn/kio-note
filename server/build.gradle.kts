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

    linuxX64 {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        compilerOptions {
            freeCompilerArgs.set(listOf("-Xcontext-parameters"))
        }
        jvmMain.dependencies {
            implementation(libs.kio.poller.select)
        }
        linuxMain.dependencies {
            implementation(libs.kio.poller.uring)
        }
        commonMain.dependencies {
            implementation(libs.kio.http)
            implementation(libs.kio.tls)
            implementation(libs.kio.postgres.connection)
            implementation(libs.kio.io)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.register<Sync>("prepareKnoteDist") {
    dependsOn("linkReleaseExecutableLinuxX64")

    from(layout.buildDirectory.dir("bin/linuxX64/releaseExecutable")) {
        include("server.kexe")
        rename("server.kexe", "knote")
    }

    from(rootProject.projectDir.resolve("resource")) {
        into("resource")
    }

    into(layout.buildDirectory.dir("dist/knote"))
}