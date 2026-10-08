plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "de.ovgu.imiq.cricket"
 compileSdk = 36
 defaultConfig { applicationId = "de.ovgu.imiq.cricket"; minSdk = 26; targetSdk = 36; versionCode = 10; versionName = "0.3.0" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.10.00"))
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.activity:activity-compose:1.11.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
