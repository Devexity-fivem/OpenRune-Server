plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.invtx)
    implementation(projects.api.playerOutput)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.content.interfaces.omnishop)
    implementation(projects.content.quest)
    implementation(projects.content.skills.mining)
    implementation(projects.content.skills.runecrafting)
    testImplementation(libs.fastutil)
}
