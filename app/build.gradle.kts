plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "edu.utsa.cs3443.adminproductuploader"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.utsa.cs3443.adminproductuploader"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures{
        viewBinding = true
    }

}

buildscript {
    repositories {
        google()
        mavenCentral()
        //noinspection JcenterRepositoryObsolete
        jcenter()
    }

    dependencies{
        //classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    }
}

dependencies {

    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.skydoves.colorpickerview)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    //Coroutines to make Firebase work
    implementation(libs.kotlinx.coroutines.play.services)
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.13.1")
    }
}