# Cloud Agent development image for the Aulama Anime TV Android project.
# Provides JDK 17 (required by the Gradle/AGP toolchain) and a headless
# Android SDK (platform 34 + build-tools 34.0.0) so `./gradlew` can build
# the app and run unit tests without any interactive setup.
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# System toolchain: JDK 17 plus the utilities needed to fetch and unpack the
# Android command-line tools. git/curl are required in fully custom images.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        ca-certificates \
        curl \
        git \
        unzip \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk

# Install the Android command-line tools into the layout the SDK manager
# expects ($ANDROID_HOME/cmdline-tools/latest), then accept licenses and
# install the exact platform/build-tools the project pins (compileSdk 34).
ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools \
    && mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools

ENV PATH="${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /dev/null

# Run as a non-root user that owns the SDK and Gradle home so the agent can
# write caches and generated build output.
RUN useradd --create-home --shell /bin/bash --uid 1000 ubuntu \
    && apt-get update \
    && apt-get install -y --no-install-recommends sudo \
    && rm -rf /var/lib/apt/lists/* \
    && echo 'ubuntu ALL=(ALL) NOPASSWD:ALL' > /etc/sudoers.d/ubuntu \
    && chmod 0440 /etc/sudoers.d/ubuntu \
    && chown -R ubuntu:ubuntu "${ANDROID_HOME}"

USER ubuntu
