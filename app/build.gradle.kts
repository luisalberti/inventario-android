plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "cl.efficientchile.inventario"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.efficientchile.inventario"
        // 31 y no 32: el requisito era Android 12, que es API 31.
        minSdk = 31
        targetSdk = 34
        versionCode = 2
        versionName = "2.1"
    }

    buildTypes {
        release {
            /* Sin minify a proposito. Moshi arma los adaptadores por reflexion
               sobre los nombres de las clases de datos; con R8 encogiendo, esos
               nombres cambian y el JSON del servidor deja de calzar en tiempo
               de ejecucion, no de compilacion. O sea: compila, instala, y
               revienta recien cuando el vendedor intenta vender. */
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        /* Material 3 marca como experimental media biblioteca -- TopAppBar y
           Surface con onClick, entre otras-- y el compilador lo trata como
           error, no como aviso. Poner @OptIn archivo por archivo obliga a
           acordarse cada vez que se agrega una pantalla, y el build se cae en
           GitHub Actions veinte minutos despues. Se declara una sola vez aca. */
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // Sesion guardada en el telefono.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Camara y lectura de QR.
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // La version "bundled" mete el modelo dentro del APK: el meson de un
    // vivero puede no tener red la primera vez, y con la version que lo
    // descarga al vuelo el primer escaneo del dia falla sin explicacion.
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    /* Lectura de boletas y vouchers. Corre en el telefono: no sube la foto a
       ningun servidor, no cobra por uso y funciona sin señal, que en un
       vivero pasa seguido. */
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Red.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    /* Las clases llevan @JsonClass(generateAdapter = true). Sin este
       procesador la anotacion no genera nada y Moshi cae en la reflexion:
       funciona, pero el dia que R8 renombre algo el fallo aparece recien al
       vender, no al compilar. Con el procesador, los adaptadores existen de
       verdad. */
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    implementation("androidx.exifinterface:exifinterface:1.3.7")

    testImplementation("junit:junit:4.13.2")
}
