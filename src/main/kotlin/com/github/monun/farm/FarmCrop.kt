package com.github.monun.farm

import org.bukkit.block.Block

interface FarmCrop {
    val world: FarmWorld
    val chunk: FarmChunk
    val x: Int
    val y: Int
    val z: Int

    val block: Block
        get() = world.bukkitWorld.getBlockAt(x, y, z)

    val plantedTime: Long

    val type: CropType?
}

val Block.crop: FarmCrop?
    get() {
        val x = x
        val y = y
        val z = z

        return world.farmWorld.cropAt(x, y, z)
    }
