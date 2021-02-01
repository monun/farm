package com.github.monun.farm

import org.bukkit.Chunk

interface FarmChunk {
    val world: FarmWorld
    val bukkitChunk: Chunk?
    val x: Int
    val z: Int
    val isLoaded: Boolean

    fun cropAt(x: Int, y: Int, z: Int): FarmCrop?
}