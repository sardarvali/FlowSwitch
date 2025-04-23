    plugins {
        id("com.android.application")
        id("com.google.gms.google-services")
        id("org.jetbrains.kotlin.android")
        id("org.jetbrains.kotlin.kapt")
        id("androidx.navigation.safeargs.kotlin")

    }

    android {
        namespace = "com.example.gail"
        compileSdk = 35
        packagingOptions {
            exclude("META-INF/DEPENDENCIES")
            exclude("META-INF/LICENSE")
            exclude("META-INF/NOTICE")
            exclude("META-INF/INDEX.LIST")
        }

        defaultConfig {
            applicationId = "com.example.gail"
            minSdk = 26
            targetSdk = 35
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
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        kotlinOptions {
            jvmTarget = "11"
        }


    }

    dependencies {

        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.constraintlayout)
        implementation("androidx.core:core-ktx:1.12.0")
        implementation("com.google.firebase:firebase-database")
        implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
        implementation("com.google.firebase:firebase-auth")
        implementation("com.google.firebase:firebase-firestore")
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
        implementation("com.google.firebase:firebase-auth:23.1.0")
        implementation("com.google.firebase:firebase-database:21.0.0")
        implementation("com.google.firebase:firebase-messaging:24.0.3")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        implementation(libs.firebase.database)
        implementation ("com.google.firebase:firebase-auth:23.2.0")
        implementation ("com.google.firebase:firebase-functions-ktx:20.4.0")
        implementation ("com.google.android.gms:play-services-auth:20.7.0")
        implementation ("com.google.firebase:firebase-auth-ktx:22.3.")


        implementation("com.google.firebase:firebase-auth:21.0.8")
        implementation("com.google.android.gms:play-services-auth:20.4.1")

        implementation("com.google.firebase:firebase-messaging:23.1.1")
        implementation("com.google.firebase:firebase-auth:21.1.0")
        implementation ("com.google.firebase:firebase-auth:22.1.0")
        implementation ("com.google.firebase:firebase-firestore:24.9.0")
        implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")

        implementation ("com.google.firebase:firebase-firestore:25.1.3")
        implementation ("com.google.firebase:firebase-bom:32.7.0")
        implementation ("com.google.firebase:firebase-auth")
        implementation ("com.google.firebase:firebase-firestore")
        implementation ("com.google.firebase:firebase-database")
        implementation ("com.google.firebase:firebase-firestore:25.1.2")
        implementation ("com.google.android.gms:play-services-auth:20.7.0")
//        def work_version = "2.9.0" // Check for the latest version
        implementation ("androidx.work:work-runtime-ktx:2.9.0")
        implementation ("com.itextpdf:itext7-core:7.2.5")
        implementation ("com.itextpdf:itextpdf:5.5.13.3")
        implementation ("org.apache.poi:poi:5.2.3")
        implementation ("org.apache.poi:poi-ooxml:5.2.3")
        implementation ("org.apache.logging.log4j:log4j-core:2.17.1")
        implementation ("com.jakewharton.timber:timber:5.0.1")

// Or SLF4J + Logback-Android
        implementation ("org.slf4j:slf4j-api:1.7.36")
        implementation ("com.github.tony19:logback-android:2.0.0")
        implementation ("org.apache.logging.log4j:log4j-core:2.20.0") // Use latest stable
        implementation ("org.apache.logging.log4j:log4j-api:2.20.0")
        implementation ("com.squareup.okhttp3:okhttp:4.10.0")
        implementation ("com.squareup.okhttp3:okhttp:4.10.0")
        implementation ("org.json:json:20210307")

        implementation ("com.squareup.retrofit2:retrofit:2.9.0")
        implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
        implementation ("com.squareup.okhttp3:okhttp:4.9.3")
        implementation ("com.squareup.retrofit2:retrofit:2.9.0")
        implementation ("com.squareup.retrofit2:converter-scalars:2.9.0")
        implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
        implementation ("com.squareup.retrofit2:retrofit:2.9.0")
        implementation ("com.squareup.retrofit2:converter-scalars:2.9.0")
        implementation ("com.squareup.retrofit2:retrofit:2.9.0")
        implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
        implementation ("com.squareup.retrofit2:converter-scalars:2.9.0")
        implementation ("com.squareup.okhttp3:logging-interceptor:4.9.1")
        implementation(libs.androidx.mediarouter)


        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
    }