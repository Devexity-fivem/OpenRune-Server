plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.bosses)
    implementation(projects.api.bossHpBarPlugin)
    implementation(libs.kotlin.inline.logger)
}
