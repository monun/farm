package com.github.monun.farm.internal

import com.github.monun.farm.FarmManager
import com.github.monun.farm.internal.timer.CropTimeViewer
import com.github.monun.farm.plugin.FarmPlugin
import com.github.monun.tap.fake.FakeEntityServer
import com.google.common.collect.ImmutableList
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import java.util.*

class FarmManagerImpl(
    private val fakeEntityServer: FakeEntityServer
) : FarmManager {
    private val worldsByBukkitWorld = IdentityHashMap<World, FarmWorldImpl>()

    override val worlds: List<FarmWorldImpl>
        get() = ImmutableList.copyOf(worldsByBukkitWorld.values)

    internal val timeViewers = WeakHashMap<Player, CropTimeViewer>()

    override fun getWorld(bukkitWorld: World): FarmWorldImpl {
        return requireNotNull(worldsByBukkitWorld[bukkitWorld]) { "Unregistered world" }
    }

    internal fun loadWorld(world: World) {
        worldsByBukkitWorld.computeIfAbsent(world) {
            val farmWorld = FarmWorldImpl(world.name, it)
            FarmInternal.io.saveWorld(farmWorld)

            for (chunk in world.loadedChunks) {
                farmWorld.loadChunk(chunk)
            }

            farmWorld
        }
    }

    internal fun unloadWorld(world: World) {
        worldsByBukkitWorld.remove(world)?.destroy()
    }

    internal fun addTimer(player: Player, crop: FarmCropImpl) {
        val fakeStand = fakeEntityServer.spawnEntity(
            crop.block.location.apply { add(0.5, 0.75, 0.5) },
            ArmorStand::class.java
        )

        timeViewers.put(player, CropTimeViewer(player, fakeStand, crop).apply { show() })?.remove()
    }

    internal fun removeTimer(player: Player) {
        timeViewers.remove(player)?.remove()
    }

    internal fun destroy() {
        for (world in worlds) {
            world.destroy()
        }
        worldsByBukkitWorld.clear()

        for (viewer in timeViewers.values) {
            viewer.remove()
        }
        timeViewers.clear()
    }
}