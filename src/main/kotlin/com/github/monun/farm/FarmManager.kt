package com.github.monun.farm

import org.bukkit.World

interface FarmManager {
    val worlds: List<FarmWorld>

    fun getWorld(bukkitWorld: World): FarmWorld
}