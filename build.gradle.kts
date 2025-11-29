// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.1" apply false
    // 将 Kotlin 版本从 1.9.20 改为 1.9.0 (更稳定)
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    // Update KSP version to match Kotlin 1.9.20
    id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
}