import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mingi.foodrecipe"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mingi.foodrecipe"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }

        // local.properties의 GEMINI_API_KEY를 BuildConfig.GEMINI_API_KEY로 주입
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) load(f.inputStream())
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps["GEMINI_API_KEY"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${localProps["FIREBASE_API_KEY"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${localProps["FIREBASE_APP_ID"] ?: ""}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${localProps["FIREBASE_PROJECT_ID"] ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    androidResources {
        noCompress += "tflite"
    }

    packaging {
        jniLibs {
            // TFLite .so 파일이 16KB 정렬 미지원 → 파일시스템 추출 방식으로 우회 (Android 15 대응)
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // TFLite 모델 추론
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Gemini API
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Firebase Auth
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-auth")

    // UI 컴포넌트 (fragment_camera.xml에서 사용)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // KTX 확장 — viewModels, viewModelScope, repeatOnLifecycle, lifecycleScope 사용에 필요
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
