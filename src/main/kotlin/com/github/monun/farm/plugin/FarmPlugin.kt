package com.github.monun.farm.plugin

import com.github.monun.farm.CropType
import com.github.monun.farm.Farm
import com.github.monun.farm.command.FarmCommand
import com.github.monun.farm.internal.FarmInternal
import com.github.monun.kommand.kommand
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * @author Nemo
 */
class FarmPlugin : JavaPlugin() {
    override fun onEnable() {
        CropType.load(File(dataFolder, "crops-config.yml"))

        for (type in CropType.types) {
            logger.info("${type.name} = ${type.duration}")
        }

        FarmInternal.initialize(this)
        Farm.manager = FarmInternal.manager

        setupCommands()
    }

    private fun setupCommands() {
        kommand {
            FarmCommand.register(this)
        }
    }

    override fun onDisable() {
        FarmInternal.disable()
    }
}