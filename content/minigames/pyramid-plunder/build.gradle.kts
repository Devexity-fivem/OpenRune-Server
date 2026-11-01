plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.mechanics.toxins)
    implementation(projects.api.npc)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.repo)
    implementation(projects.api.scriptAdvanced)
    testImplementation(libs.fastutil)
}
