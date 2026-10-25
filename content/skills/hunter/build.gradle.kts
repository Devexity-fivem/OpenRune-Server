plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)

    integrationImplementation(projects.api.gameProcess)
    integrationImplementation(projects.engine.game)
}
