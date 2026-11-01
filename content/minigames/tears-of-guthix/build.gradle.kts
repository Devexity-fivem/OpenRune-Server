plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.repo)
    implementation(projects.api.random)
    implementation(projects.api.playerOutput)
    implementation(projects.api.invtx)
    implementation(projects.api.utils.utilsTime)
    implementation(projects.content.quest)
    testImplementation(libs.fastutil)
}
