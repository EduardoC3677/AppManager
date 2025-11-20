## Gemini CLI Context

This document provides context for the Gemini CLI to understand the AppManager project.

### Project Overview

AppManager is a versatile open-source Android application designed for managing installed apps. It offers a wide range of features, from basic information display to advanced operations requiring root or ADB access. The app is built with Material 3, providing a modern and customizable user interface. It is licensed under GPLv3+.

### Building and Running

The project is a standard Android application built with Gradle.

**Requirements:**
- JDK 17+
- Android Studio or IntelliJ IDEA
- Gradle

**Build Commands:**
- To build a debug APK:
  ```bash
  ./gradlew assembleDebug
  ```
- To create a universal debug APK:
  ```bash
  ./gradlew packageDebugUniversalApk
  ```
- The project can also be opened and built directly within Android Studio.

**Testing:**
- To run unit tests:
  ```bash
  ./gradlew test
  ```

### Development Conventions

- **Commits:** All commits must be signed off. Use `git commit -s` to add the `Signed-off-by` line.
- **Licensing:** Contributions are licensed under `GPL-3.0-or-later`.
- **Contributions:** Pull requests are the preferred method of contribution and should be directed to the main GitHub repository. For more detailed guidelines, refer to the `CONTRIBUTING.rst` file.
- **CI/CD:** GitHub Actions are used for building, testing, and creating releases. The relevant workflows can be found in the `.github/workflows` directory.
