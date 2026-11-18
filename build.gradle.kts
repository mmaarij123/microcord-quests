import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("dev.mantikafasi.manticord.plugin")
}

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use(::load)
}

manticordPlugin {
    id.set("microcord.questhome")
    entrypoint.set("dev.microcord.questhome.QuestHomePlugin")
    description.set("Browse and manage Discord Quests in Microcord Settings")
    manticordVersionMinimum.set(1)
    manticordVersionMaximumExclusive.set(2)
    usesResources.set(true)
    async()
}

android {
    namespace = "dev.microcord.questhome"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.microcord.questhome"
        minSdk = 23
        targetSdk = 36
        versionCode = 6
        versionName = "2.2.0"
    }

    signingConfigs {
        if (releaseSigningFile.exists()) create("release") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    val localPluginApi = file("../manticordrebuild/build/plugin-sdk/manticord-plugin-api-1.0.6.jar")
    compileOnly(
        if (localPluginApi.exists()) files(localPluginApi)
        else "dev.mantikafasi.manticord:plugin-api:1.0.6"
    )
    compileOnly(libs.rxjava)
    compileOnly(libs.gson)
    compileOnly(libs.retrofit)
    compileOnly(libs.appcompat)
    compileOnly(libs.fresco)
    compileOnly(libs.exoplayer.core)
    compileOnly(libs.exoplayer.ui)
    implementation(libs.exoplayer.hls) { isTransitive = false }
    testImplementation(libs.gson)
    testImplementation(libs.retrofit)
    testImplementation(libs.retrofit.gson)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.junit)
}
