plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.death)
    implementation(projects.api.repo)
    implementation(projects.api.random)
    implementation(projects.api.playerOutput)
    implementation(projects.api.invtx)
}
