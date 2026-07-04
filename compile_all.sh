#!/bin/sh
./gradlew :app:compileDebugSources :desktop:compileKotlin -q
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination "platform=iOS Simulator,id=3A708C52-7159-4DF0-A204-89F6C206F309" build -quiet
