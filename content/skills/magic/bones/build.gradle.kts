plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.combat.combatManager)
    implementation(projects.api.generated)
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.script)
    implementation(projects.api.spells)
    implementation(projects.api.spellsRunes)
}
