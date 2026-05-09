#!/bin/sh
# GemmaChat Setup Script
# Run this ONCE before building. Works in Termux on Android.

echo "=== GemmaChat Setup ==="

# Step 1: Install dependencies (Termux)
pkg update -y
pkg install openjdk-17 wget unzip -y

# Step 2: Download Gradle wrapper jar (required by gradlew)
mkdir -p gradle/wrapper
wget -q "https://repo1.maven.org/maven2/org/gradle/gradle-wrapper/8.4/gradle-wrapper-8.4.jar" \
     -O gradle/wrapper/gradle-wrapper.jar \
  || wget -q "https://services.gradle.org/distributions/gradle-8.4-bin.zip" -O /tmp/gradle.zip

echo "Setup complete. Now run: ./gradlew assembleDebug"
