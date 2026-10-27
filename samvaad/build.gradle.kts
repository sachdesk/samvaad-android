import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.samvaads.widget"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.browser:browser:1.7.0")

    testImplementation("junit:junit:4.13.2")
}

// Coordinates come from gradle.properties (GROUP, POM_ARTIFACT_ID, VERSION_NAME).
mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        )
    )

    // Uploads through the Central Portal (central.sonatype.com) and releases automatically
    // once validation passes.
    publishToMavenCentral(automaticRelease = true)

    // Signing is only required when a key is supplied (CI release). Local
    // publishToMavenLocal works without one.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set("Samvaad Android SDK")
        description.set(
            "Embed the Samvaad customer chat widget in Android apps: open the chat, " +
                "identify users, track unread messages and handle push notifications."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/sachdesk/samvaad-android")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("sachdesk")
                name.set("Sachdesk")
                email.set("sachin@sachdesk.com")
                url.set("https://github.com/sachdesk")
            }
        }
        scm {
            url.set("https://github.com/sachdesk/samvaad-android")
            connection.set("scm:git:git://github.com/sachdesk/samvaad-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/sachdesk/samvaad-android.git")
        }
    }
}
