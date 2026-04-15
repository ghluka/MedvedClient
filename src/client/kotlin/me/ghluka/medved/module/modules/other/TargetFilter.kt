package me.ghluka.medved.module.modules.other

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

object TargetFilter : Module(
    name = "Target Filter",
    description = "Filters targets across combat modules (anti-bot, teams, etc)",
    category = Category.OTHER
) {
    override val showInModulesList = false

    private val ignoreTeam  = boolean("ignore team", true)
    private val ignoreColor = boolean("ignore color", true)
    private val ignoreInvis = boolean("ignore invisible", true)
    private val antiBot     = boolean("anti-bot", true)

    fun isValidTarget(player: Player, target: LivingEntity): Boolean {
        if (!isEnabled()) return true

        if (ignoreInvis.value && target.isInvisible) {
            return false
        }

        if (ignoreTeam.value) {
            val pTeam = player.team
            val tTeam = target.team
            if (pTeam != null && tTeam != null && pTeam.isAlliedTo(tTeam)) {
                return false
            }
        }

        if (antiBot.value && target is Player) {
            val connection = Minecraft.getInstance().connection
            val info = connection?.getPlayerInfo(target.uuid)

            if (info == null || info.latency < 0) {
                return false
            }
            
            if (target.uuid == player.uuid) return false
            
            val name = target.name.string
            if (name.isEmpty() || name.contains(" ") || name.startsWith("§")) return false
            
            if (info.latency == 0 || info.latency > 100000) return false
            if (target.tickCount < 10) return false
        }

        if (ignoreColor.value && target is Player) {
            val pHead = player.getItemBySlot(EquipmentSlot.HEAD)
            val tHead = target.getItemBySlot(EquipmentSlot.HEAD)

            if (!pHead.isEmpty && !tHead.isEmpty) {
                val pColor = pHead.get(DataComponents.DYED_COLOR)?.rgb()
                val tColor = tHead.get(DataComponents.DYED_COLOR)?.rgb()

                if (pColor != null && tColor != null && pColor == tColor) {
                    return false
                }
            }
        }

        return true
    }
}