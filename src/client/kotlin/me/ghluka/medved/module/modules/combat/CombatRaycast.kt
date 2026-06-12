package me.ghluka.medved.module.modules.combat

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult

internal fun entityRaycast(
    player: Player,
    range: Double,
    predicate: (Entity) -> Boolean = { true },
): EntityHitResult? {
    val level = Minecraft.getInstance().level ?: return null
    val eye = player.eyePosition
    val end = eye.add(player.lookAngle.scale(range))

    val blockHit = player.pick(range, 1.0f, false)
    val maxDistanceSq = if (blockHit.type == HitResult.Type.MISS) {
        range * range
    } else {
        eye.distanceToSqr(blockHit.location).coerceAtMost(range * range)
    }

    var best: Entity? = null
    var bestHit = end
    var bestDistanceSq = maxDistanceSq

    for (entity in level.entitiesForRendering()) {
        if (entity === player || !entity.isPickable || !predicate(entity)) continue

        val box = entity.boundingBox.inflate(entity.pickRadius.toDouble())
        val hit = when {
            box.contains(eye) -> eye
            else -> box.clip(eye, end).orElse(null) ?: continue
        }
        val distanceSq = eye.distanceToSqr(hit)
        if (distanceSq <= bestDistanceSq) {
            best = entity
            bestHit = hit
            bestDistanceSq = distanceSq
        }
    }

    return best?.let { EntityHitResult(it, bestHit) }
}
