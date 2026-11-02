plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.invtx)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.content.interfaces.omnishop)
    implementation(projects.content.skills.mining)
    testImplementation(libs.fastutil)
}
