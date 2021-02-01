package com.github.monun.farm.internal

import com.github.monun.farm.CropType
import org.bukkit.Effect
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.type.Farmland
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldInitEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.bukkit.inventory.ItemStack

class EventListener(private val manager: FarmManagerImpl) : Listener {
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onWorldInit(event: WorldInitEvent) {
        manager.loadWorld(event.world)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onWorldUnload(event: WorldUnloadEvent) {
        manager.unloadWorld(event.world)
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        manager.getWorld(event.world).loadChunk(event.chunk)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        event.chunk.let { chunk -> manager.getWorld(event.world).unloadChunk(chunk.x, chunk.z) }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onBlockCanBuild(event: BlockCanBuildEvent) {
        if (!event.isBuildable) return

        val blockData = event.blockData
        val cropType = CropType.getByBlockData(blockData)

        if (cropType != null) {
            val block = event.block
            manager.getWorld(block.world).addCrop(block.x, block.y, block.z, cropType)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val blockData = block.blockData

        if (blockData.material == Material.FARMLAND) {
            event.isCancelled = true

            block.blockData = Material.AIR.createBlockData()

            val loc = block.location
            val world = loc.world

            world.playEffect(loc, Effect.STEP_SOUND, blockData.material)
            world.dropItemNaturally(loc, ItemStack(Material.FARMLAND)).apply {
                pickupDelay = 10
            }
        } else {
            val cropType = CropType.getByBlockData(blockData)

            if (cropType != null) {
                manager.getWorld(block.world).removeCrop(block.x, block.y, block.z)
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.block
        var blockData = block.blockData

        if (blockData.material == Material.DIRT && event.itemInHand.type == Material.FARMLAND) {
            // Issue #2 https://github.com/noonmaru/farm/issues/2
            blockData = Material.FARMLAND.createBlockData()
        }

        if (blockData.material == Material.FARMLAND) {
            val farmland = blockData as Farmland
            farmland.moisture = farmland.maximumMoisture
            block.blockData = blockData
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST,
        ignoreCancelled = true
    ) // Issue #1 https://github.com/noonmaru/farm/issues/1
    fun onBlockForm(event: BlockFadeEvent) {
        if (event.block.type == Material.FARMLAND) {
            event.isCancelled = true
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST,
        ignoreCancelled = true
    ) // Issue #1 https://github.com/noonmaru/farm/issues/1
    fun onBlockGrow(event: BlockDispenseEvent) {
        if (event.item.type == Material.BONE_MEAL) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action == Action.RIGHT_CLICK_BLOCK) { //뼛가루 방지
            event.item?.let { item ->
                if (item.type == Material.BONE_MEAL) {
                    event.isCancelled = true
                    return
                }

                val cropType = CropType.getBySeedType(item.type)

                if (cropType != null && item.getEnchantmentLevel(Enchantment.DURABILITY) == 0) {
                    event.isCancelled = true
                    return
                }
            }

            event.clickedBlock?.let { block ->
                val blockData = block.blockData
                val cropType = CropType.getByBlockType(blockData.material)

                if (cropType != null) {
                    val player = event.player

                    if (player.isSneaking) { // 타이머 보기
                        val crop = block.crop

                        if (crop != null) {
                            manager.addTimer(player, crop)
                        }
                        event.isCancelled = true
                        return
                    }

                    if (cropType === CropType.SWEET_BERRY) { //달콤한 열매 상호작용
                        val ageable = blockData as Ageable

                        if (ageable.age >= 2) { //열매를 채취할 수 있는 단계
                            val world = manager.getWorld(block.world)
                            world.addCrop(block.x, block.y, block.z, cropType)
                            block.blockData = cropType.firstStage.blockData
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        manager.removeTimer(event.player)
    }
}