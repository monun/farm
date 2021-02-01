package com.github.monun.farm

import org.bukkit.World

interface FarmWorld {
    val name: String
    val bukkitWorld: World
    val loadedChunks: List<FarmChunk>

    fun chunkAt(x: Int, z: Int): FarmChunk?

    fun cropAt(x: Int, y: Int, z: Int): FarmCrop?
}

val World.farmWorld: FarmWorld
    get() {
        return Farm.manager.getWorld(this)
    }