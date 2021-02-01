package com.github.monun.farm.internal

import com.github.monun.farm.CropStage
import com.github.monun.farm.CropType
import com.github.monun.farm.FarmCrop
import com.github.monun.farm.util.UpstreamReference
import org.bukkit.Effect

class FarmCropImpl(
    chunk: FarmChunkImpl,
    x: Int,
    y: Int,
    z: Int,
    override val plantedTime: Long
) : FarmCrop, Indexable, Comparable<FarmCropImpl> {
    override val world: FarmWorldImpl
        get() = chunk.world

    private val chunkRef: UpstreamReference<FarmChunkImpl> = UpstreamReference(chunk)

    override val chunk: FarmChunkImpl
        get() = chunkRef.get()

    override val index: Short = indexOf(x, y, z)

    override val x: Int
        get() = (chunk.x shl 4) or index.toX()

    override val y: Int
        get() = index.toY()

    override val z: Int
        get() = (chunk.z shl 4) or index.toZ()

    override val type: CropType?
        get() = CropType.getByBlockData(block.blockData)

    internal var nextGrowthTime: Long = -1L
        private set

    internal var queueSlot: Int = -1

    internal val isQueued: Boolean
        get() = queueSlot >= 0

    internal fun update(currentTime: Long = System.currentTimeMillis()): UpdateResult {
        if (!chunk.isLoaded) return UpdateResult.FAIL_CHUNK_UNLOADED

        val block = block
        val blockData = block.blockData
        val type = CropType.getByBlockData(blockData) ?: return UpdateResult.FAIL_TYPE_MISMATCH
        val stage = type.getStageByElapsedTime(currentTime - plantedTime)
        stage.update(block, blockData)

        if (stage is CropStage.Growth) {
            updateNextGrowthTime(stage)

            return UpdateResult.SUCCESS_GROWTH
        } else {
            nextGrowthTime = -1L
            val loc = block.location
            loc.world.playEffect(loc, Effect.VILLAGER_PLANT_GROW, 16)
        }

        return UpdateResult.SUCCESS_DONE
    }

    internal fun updateNextGrowthTime(stage: CropStage.Growth) {
        nextGrowthTime = plantedTime + stage.time
    }

    override fun compareTo(other: FarmCropImpl): Int {
        return nextGrowthTime.compareTo(other.nextGrowthTime)
    }

    enum class UpdateResult {
        SUCCESS_GROWTH,
        SUCCESS_DONE,
        FAIL_TYPE_MISMATCH,
        FAIL_CHUNK_UNLOADED
    }
}

fun indexOf(x: Int, y: Int, z: Int): Short {
    return ((y shl 8) or ((z and 0xF) shl 4) or (x and 0xF)).toShort()
}

private fun Short.toY(): Int {
    return (toInt() shr 8) and 0xFF
}

private fun Short.toZ(): Int {
    return (toInt() shr 4) and 0xF
}

private fun Short.toX(): Int {
    return toInt() and 0xF
}