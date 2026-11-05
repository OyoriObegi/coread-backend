import groovy.json.JsonSlurper

plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.plugin.serialization")
 id("org.jetbrains.kotlin.kapt")
 id("com.google.dagger.hilt.android")
}
if(file("google-services.json").exists()) apply(plugin="com.google.gms.google-services")
val firebaseProjectId = if(file("google-services.json").exists()) {
 val config=JsonSlurper().parse(file("google-services.json")) as Map<*,*>
 (config["project_info"] as Map<*,*>)["project_id"].toString()
} else "unconfigured-coread"
android {
 namespace="com.coread.app"
 compileSdk=35
 defaultConfig { applicationId="com.coread.app";minSdk=26;targetSdk=35;versionCode=5;versionName="1.0.4";manifestPlaceholders["firebaseAuthHost"]=firebaseProjectId+".firebaseapp.com";resourceConfigurations+=listOf("en") }
 buildTypes { release { isMinifyEnabled=true;isShrinkResources=true;proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 defaultConfig { buildConfigField("String","COREAD_API_BASE_URL","\"https://coread-backend.onrender.com\"") }
 buildFeatures { compose=true;buildConfig=true }
 packaging { resources.excludes+=setOf("META-INF/AL2.0","META-INF/LGPL2.1","META-INF/INDEX.LIST","META-INF/DEPENDENCIES") }
}
kapt { correctErrorTypes=true }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.04.01"))
 implementation("androidx.core:core-ktx:1.16.0")
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
 implementation("androidx.room:room-runtime:2.7.1")
 implementation("androidx.room:room-ktx:2.7.1")
 implementation("androidx.room:room-paging:2.7.1")
 kapt("androidx.room:room-compiler:2.7.1")
 implementation("androidx.paging:paging-compose:3.3.6")
 implementation("androidx.datastore:datastore-preferences:1.1.4")
 implementation("androidx.work:work-runtime-ktx:2.10.1")
 implementation("com.google.dagger:hilt-android:2.56.2")
 kapt("com.google.dagger:hilt-compiler:2.56.2")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.squareup.retrofit2:retrofit:2.11.0")
 implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-firestore")
 implementation("com.google.firebase:firebase-messaging")
 implementation("androidx.credentials:credentials:1.5.0")
 implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
 implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
 testImplementation("junit:junit:4.13.2")
}
tasks.register("checkReleaseSize") { dependsOn("assembleRelease");doLast { val apk=file("build/outputs/apk/release/app-release-unsigned.apk");check(apk.isFile && apk.length()<=5_000_000) { "APK exceeds 5 MB: "+apk.length() } } }
