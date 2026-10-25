plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.random)
    implementation(projects.api.registry)
    implementation(projects.api.repo)
    implementation(projects.content.skills.utils)
    testImplementation(libs.fastutil)
    integrationImplementation(projects.api.registry)
    integrationImplementation(projects.engine.game)
}
