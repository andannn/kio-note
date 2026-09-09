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
            implementation(project(":db:schema"))
            implementation(libs.kio.http)
            implementation(libs.hash.sha2)
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

    val executableDir =
        layout.buildDirectory.dir("bin/linuxX64/releaseExecutable")
    val executable = executableDir.map { it.file("app.kexe") }

    doFirst {
        val file = executable.get().asFile
        if (!file.isFile) {
            throw GradleException("no executable：${file.absolutePath}")
        }
    }

    from(layout.buildDirectory.dir("bin/linuxX64/releaseExecutable")) {
        include("app.kexe")
        rename("app.kexe", "knote")
    }

    from(layout.projectDirectory.dir("resource")) {
        into("resource")
    }

    into(layout.buildDirectory.dir("dist/knote"))
}