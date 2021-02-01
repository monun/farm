package com.github.monun.farm.internal

import com.github.monun.farm.plugin.FarmPlugin
import com.github.monun.tap.fake.FakeEntityServer
import org.bukkit.Bukkit
import org.bukkit.block.Block
import java.io.File

internal object FarmInternal {
    lateinit var io: FarmIO
    lateinit var task: CropTask
    lateinit var fakeEntityServer: FakeEntityServer
    lateinit var manager: FarmManagerImpl

    internal fun initialize(plugin: FarmPlugin) {
        io = FarmIOSQLite(plugin, File(plugin.dataFolder, "database/crops.db"))
        task = CropTask()
        fakeEntityServer = FakeEntityServer.create(plugin)
        manager = FarmManagerImpl(fakeEntityServer)
        manager.apply {
            for (world in Bukkit.getWorlds()) {
                loadWorld(world)
            }
        }

        task.fakeEntityServer = fakeEntityServer

        plugin.server.apply {
            scheduler.runTaskTimer(plugin, task, 0L, 1L)
            pluginManager.registerEvents(EventListener(manager), plugin)
        }
    }

    internal fun disable() {
        io.close()
        fakeEntityServer.clear()
        manager.destroy()
    }
}

internal val Block.crop: FarmCropImpl?
    get() {
        return FarmInternal.manager.getWorld(world).cropAt(x, y, z)
    }