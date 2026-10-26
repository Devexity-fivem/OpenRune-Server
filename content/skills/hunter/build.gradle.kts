plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.registry)
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)

    integrationImplementation(projects.api.gameProcess)
    integrationImplementation(projects.api.dropTable)
    integrationImplementation(projects.api.dropTablePlugin)
    integrationImplementation(projects.content.drops)
    integrationImplementation(projects.content.quest)
    integrationImplementation(projects.engine.coroutine)
    integrationImplementation(projects.engine.game)
}
