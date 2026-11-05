package org.rsmod.content.quest.manager

import org.rsmod.api.combat.commons.magic.SpellQuestRequirement
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.aggression.AggressionGate
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.content.generic.locs.bookcases.BookcaseSearchHook
import org.rsmod.content.quest.area.baxtorianfalls.waterfall.TouristCentreBookcases
import org.rsmod.content.quest.area.corsaircove.CorsairCurse
import org.rsmod.content.quest.area.corsaircove.CorsairCurseIthoiKillHook
import org.rsmod.content.quest.area.falador.doricsquest.DoricsQuest
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasure
import org.rsmod.content.quest.area.lumbridge.RuneMysteriesQuest
import org.rsmod.content.quest.area.lumbridge.idesofmilk.IdesOfMilkQuest
import org.rsmod.content.quest.area.lumbridge.xmarksthespot.XMarksTheSpotQuest
import org.rsmod.content.quest.area.rimmington.WitchsPotionRatTail
import org.rsmod.content.quest.area.varrock.daddyshome.DaddysHomeQuest
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerDrops
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerWormbrainGate
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerWormbrainTable
import org.rsmod.content.quest.area.varrock.gertrudescat.GertrudesCatQuest
import org.rsmod.plugin.module.PluginModule

public class QuestModule : PluginModule() {
    override fun bind() {
        bindInstance<QuestRequirementResolver>()
        bindInstance<RuneMysteriesQuest>()
        bindInstance<GertrudesCatQuest>()
        bindInstance<DaddysHomeQuest>()
        bindInstance<XMarksTheSpotQuest>()
        bindInstance<DoricsQuest>()
        bindInstance<IdesOfMilkQuest>()
        bindInstance<KnightsSwordQuest>()
        bindInstance<CorsairCurse>()
        addSetBinding<SpellQuestRequirement>(PolicySpellQuestRequirement::class.java)
        addSetBinding<AggressionGate>(QuestAggressionGate::class.java)
        addSetBinding<BookcaseSearchHook>(TouristCentreBookcases::class.java)
        addSetBinding<SpadeDigHook>(XMarksTheSpotQuest::class.java)
        addSetBinding<SpadeDigHook>(PiratesTreasure::class.java)
        addSetBinding<SpadeDigHook>(CorsairCurse::class.java)
        addSetBinding<NpcDeathKillHook>(WitchsPotionRatTail::class.java)
        addSetBinding<NpcDeathKillHook>(DragonSlayerDrops::class.java)
        addSetBinding<NpcDeathKillHook>(CorsairCurseIthoiKillHook::class.java)
        addSetBinding<NpcDeathDropHook>(DragonSlayerWormbrainTable::class.java)
        addSetBinding<NpcAttackValidateHook>(DragonSlayerWormbrainGate::class.java)
    }
}
