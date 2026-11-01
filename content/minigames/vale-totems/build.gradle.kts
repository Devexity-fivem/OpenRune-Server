plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.playerOutput)
    implementation(projects.api.invtx)
    implementation(projects.content.skills.utils)
    testImplementation(libs.fastutil)
}
