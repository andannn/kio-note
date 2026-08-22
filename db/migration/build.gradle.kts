plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("MainKt")
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
        jvmMain.dependencies {
            implementation(libs.kio.poller.select)
        }
        linuxMain.dependencies {
            implementation(libs.kio.poller.uring)
        }
        commonMain.dependencies {
            implementation(project(":db:schema"))
            implementation(libs.kio.postgres.connection)
            implementation(libs.kio.io)
        }
    }
}

tasks.register<Sync>("prepareDBMigrationTool") {
    dependsOn("linkReleaseExecutableLinuxX64")

    from(layout.buildDirectory.dir("bin/linuxX64/releaseExecutable")) {
        include("migration.kexe")
        rename("migration.kexe", "knote_db_migration")
    }

    into(layout.buildDirectory.dir("dist/knote"))
}