#!/bin/sh
./compile_all.sh
./gradlew testDebugUnitTest testAndroidHostTest desktopTest :desktop:test iosSimulatorArm64Test
