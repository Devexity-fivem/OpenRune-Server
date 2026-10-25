plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.objCharges)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.repo)
    implementation(projects.api.weapons)

    integrationImplementation(projects.api.objCharges)
}
