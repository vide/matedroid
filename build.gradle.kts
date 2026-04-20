plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt)
}

// Minimal detekt setup — only the rules that catch actual dead code, to keep the signal high
// and the noise low. The config file is a tiny allow-list; everything else is off.
detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    source.setFrom(files("app/src/main/java"))
}
