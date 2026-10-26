package org.rsmod.content.raids.tfa.party

import jakarta.inject.Inject
import org.rsmod.api.instances.InstanceEnterTransition
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.withInstanceEnterTransition
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.raids.party.RaidParty
import org.rsmod.content.raids.tfa.raid.TfaRaidHud
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusEncounter
import org.rsmod.content.raids.tfa.raid.encounter.ocellus.OcellusFight
import org.rsmod.game.MapClock
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TfaRaidEntryScript
@Inject
constructor(
    private val parties: TfaParties,
    private val lobby: TfaLobby,
    private val partyScript: TfaPartyScript,
    private val hud: TfaRaidHud,
    private val ocellus: OcellusEncounter,
    private val manager: InstanceManager,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.tfa_lobby_raid_entry") { enter() }
    }

    private suspend fun ProtectedAccess.enter() {
        arriveDelay()
        val party = parties.partyOf(player)
        if (party == null) {
            val form =
                choice2(
                    "Form or join a party.",
                    true,
                    "Cancel.",
                    false,
                    title = "You are currently not in a raiding party.",
                )
            if (form) with(partyScript) { openList() }
            return
        }
        val room = liveRoom(party)
        when {
            room != null -> join(room)
            party.isLeader(player) -> start(party)
            else -> {
                val leader = party.leader?.displayName ?: "party leader"
                mesbox("Your leader, $leader, must enter first.")
            }
        }
    }

    private suspend fun ProtectedAccess.start(party: RaidParty<TfaPartySettings>) {
        val room =
            when (val opened = ocellus.open(player)) {
                is OcellusEncounter.Opened.Failed -> {
                    mes(opened.reason)
                    return
                }
                is OcellusEncounter.Opened.Room -> opened
            }
        if (!party.locked) {
            for (applicant in parties.lock(party)) {
                applicant.mes("The party to which you were applying has started its raid.")
                lobby.refreshDetails(applicant)
            }
        }
        lobby.bindRaid(room.fight.session.id, party)
        hud.track(room.fight.session.id)
        mes("You enter the Archive...")
        withInstanceEnterTransition(InstanceEnterTransition()) {
            telejump(room.enter, TeleportType.Exempt)
            manager.finalizeEntry(player, room.fight.session, clock.cycle)
        }
        for (member in party.members) {
            if (member === player) continue
            lobby.markStepInside(member)
            member.mes("Your party has entered the Archive. Step inside now!")
        }
    }

    private suspend fun ProtectedAccess.join(room: OcellusFight) {
        val joined = manager.join(player, room.session, clock.cycle, forceAccess = true)
        if (joined !is InstanceManager.Result.Joined) {
            mes((joined as? InstanceManager.Result.Failed)?.reason ?: "You can't enter right now.")
            return
        }
        mes("You enter the Archive...")
        withInstanceEnterTransition(InstanceEnterTransition()) {
            telejump(joined.enter, TeleportType.Exempt)
            manager.finalizeEntry(player, room.session, clock.cycle)
        }
    }

    private fun liveRoom(party: RaidParty<TfaPartySettings>): OcellusFight? {
        if (!party.locked) return null
        for (member in party.members) {
            val session = manager.sessionForPlayer(member) ?: continue
            ocellus.of(session.id)?.let { return it }
        }
        return null
    }
}
