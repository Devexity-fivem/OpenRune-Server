plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.bosses)
    implementation(projects.api.bossHpBarPlugin)
    implementation(projects.api.instances)
    implementation(libs.kotlin.inline.logger)
}
