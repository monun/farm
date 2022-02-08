package com.github.monun.farm

import com.google.common.base.CaseFormat
import com.google.common.collect.ImmutableList
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.TreeType
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

abstract class CropType(
    val name: String,
    val seedType: Material,
    val cropType: Material
) {
    private val key = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, name.replace(' ', '_'))
    abstract val stages: List<CropStage.Growth>
    abstract val result: CropStage.Result

    val firstStage
        get() = stages.first()

    var duration: Long = 0L
        internal set(value) {
            require(value >= 0L) { "Duration cannot be negative." }

            val stages = stages
            val size = stages.count()

            var prevStageTime = 0L

            for (i in 0 until size) {
                val stage = stages[i]
                val stageTime = value * (i + 1) / size
                val stageDuration = stageTime - prevStageTime

                stage.time = stageTime
                stage.duration = stageDuration

                prevStageTime = stageTime
            }

            field = value
        }

    private fun postInit() {
        var prev: CropStage.Growth? = null
        var time = 0L

        for (stage in stages) {
            time += stage.duration
            stage.time = time
            prev?.apply { nextStage = stage }
            prev = stage
        }

        if (prev != null)
            prev.nextStage = result
    }

    open fun isCrop(blockData: BlockData): Boolean = true

    fun getStageByElapsedTime(elapsedTime: Long): CropStage {
        for (stage in stages) {
            if (elapsedTime < stage.time)
                return stage
        }

        return result
    }

    companion object {
        //crops
        val BEAT: CropType = Crops("Beat", Material.BEETROOT_SEEDS, Material.BEETROOTS)
        val CARROT: CropType = Crops("Carrot", Material.CARROT, Material.CARROTS)
        val COCOA: CropType = Crops("Cocoa", Material.COCOA_BEANS, Material.COCOA)
        val NETHER_WART: CropType = Crops("Nether wart", Material.NETHER_WART, Material.NETHER_WART)
        val POTATO: CropType = Crops("Potato", Material.POTATO, Material.POTATOES)
        val SWEET_BERRY: CropType = Crops("Sweet berry", Material.SWEET_BERRIES, Material.SWEET_BERRY_BUSH)
        val WHEAT: CropType = Crops("Wheat", Material.WHEAT_SEEDS, Material.WHEAT)

        //stems
        val MELON: CropType =
            StemCrops("Melon", Material.MELON_SEEDS, Material.MELON_STEM, Material.MELON.createBlockData())
        val PUMPKIN: CropType =
            StemCrops("Pumpkin", Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM, Material.PUMPKIN.createBlockData())

        //trees
        val ACACIA_TREE: CropType = Tree("Acacia tree", Material.ACACIA_SAPLING, TreeType.ACACIA)
        val BIRCH_TREE: CropType = Tree("Birch tree", Material.BIRCH_SAPLING, TreeType.BIRCH)
        val DARK_OAK_TREE: CropType = Tree("Dark oak tree", Material.DARK_OAK_SAPLING, TreeType.DARK_OAK)
        val JUNGLE_TREE: CropType = Tree("Jungle tree", Material.JUNGLE_SAPLING, TreeType.SMALL_JUNGLE)
        val OAK_TREE: CropType = Tree("Oak tree", Material.OAK_SAPLING, TreeType.TREE)
        val SPRUCE_TREE: CropType = Tree("Spruce tree", Material.SPRUCE_SAPLING, TreeType.REDWOOD)

        //mushrooms
        val BROWN_MUSHROOM: CropType = Tree("Brown mushroom", Material.BROWN_MUSHROOM, TreeType.BROWN_MUSHROOM)
        val RED_MUSHROOM: CropType = Tree("Red mushroom", Material.RED_MUSHROOM, TreeType.RED_MUSHROOM)

        val types: List<CropType>
        private val typesBySeed = EnumMap<Material, CropType>(Material::class.java)
        private val typesByBlock = EnumMap<Material, CropType>(Material::class.java)

        init {
            types = ImmutableList.of(
                BEAT,
                CARROT,
                COCOA,
                NETHER_WART,
                POTATO,
                SWEET_BERRY,
                WHEAT,
                MELON,
                PUMPKIN,
                ACACIA_TREE,
                BIRCH_TREE,
                DARK_OAK_TREE,
                JUNGLE_TREE,
                OAK_TREE,
                SPRUCE_TREE,
                BROWN_MUSHROOM,
                RED_MUSHROOM
            )

            for (type in types) {
                type.postInit()
                type.duration = 300000L

                require(type.cropType !in typesByBlock) { "Already registered material ${type.name}" }

                typesBySeed[type.seedType] = type
                typesByBlock[type.cropType] = type
            }
        }

        fun getByBlockData(blockData: BlockData): CropType? {
            return getByBlockType(blockData.material)?.takeIf { it.isCrop(blockData) }
        }

        fun getByBlockType(material: Material): CropType? {
            return typesByBlock[material]
        }

        fun getBySeedType(material: Material): CropType? {
            return typesBySeed[material]
        }

        internal fun load(file: File) {
            if (file.exists()) {
                val config = YamlConfiguration.loadConfiguration(file)
                var changed = false

                for (type in types) {
                    if (config.contains(type.key)) {
                        type.duration = max(0L, config.getLong(type.key))
                    } else {
                        config[type.key] = type.duration
                        changed = true
                    }
                }

                if (changed) {
                    config.save(file)
                }
            } else {
                val config = YamlConfiguration()

                for (type in types) {
                    config[type.key] = type.duration
                }

                file.parentFile.mkdirs()
                config.save(file)
            }
        }
    }

    internal class Crops(
        name: String,
        seedItemType: Material,
        material: Material
    ) : CropType(name, seedItemType, material) {
        override val stages: List<CropStage.Growth>
        override val result: CropStage.Result

        private val maximumAge: Int

        init {
            val blockData = material.createBlockData() as Ageable
            this.maximumAge = blockData.maximumAge
            val stages = ArrayList<CropStage.Growth>(maximumAge + 1)

            for (age in 0 until maximumAge) {
                stages += CropStage.growth(material.createAgeable(age))
            }

            this.stages = ImmutableList.copyOf(stages)
            this.result = CropStage.result(material.createAgeable(maximumAge))
        }

        override fun isCrop(blockData: BlockData): Boolean {
            return (blockData as Ageable).age < maximumAge
        }
    }

    internal class StemCrops(
        name: String,
        seedType: Material,
        stemType: Material,
        resultBlockData: BlockData
    ) : CropType(name, seedType, stemType) {
        override val stages: List<CropStage.Growth>
        override val result: CropStage.Result

        init {
            val blockData = stemType.createBlockData() as Ageable
            val maximumAge = blockData.maximumAge
            val stages = ArrayList<CropStage.Growth>(maximumAge + 1)

            for (age in 0..maximumAge) {
                stages += CropStage.growth(stemType.createAgeable(age))
            }

            this.stages = ImmutableList.copyOf(stages)
            this.result = CropStage.result(resultBlockData)
        }

        override fun isCrop(blockData: BlockData): Boolean {
            return blockData.material == cropType
        }
    }

    internal class Tree(
        name: String,
        saplingType: Material,
        private val treeType: TreeType
    ) : CropType(name, saplingType, saplingType) {
        override val stages: List<CropStage.Growth> = ImmutableList.of(CropStage.growth(saplingType.createBlockData()))

        override val result: CropStage.Result = CropStage.result { block, _ ->
            val blockData = block.blockData
            val underBlock = block.location.add(0.0,-1.0,0.0).block
            val underBlockData = underBlock.blockData.clone()
            block.blockData = Material.AIR.createBlockData()
            if (!block.world.generateTree(block.location, treeType)) {
                block.blockData = blockData

                val loc = block.location.add(0.5, 0.5, 0.5)
                block.world.spawnParticle(Particle.SMOKE_LARGE, loc.x, loc.y, loc.z, 10, 0.1, 0.1, 0.1)
            }
            if(underBlockData.material==Material.FARMLAND)
                underBlock.blockData = underBlockData
        }
    }
}

private fun Material.createAgeable(age: Int): BlockData {
    return createBlockData {
        (it as Ageable).age = age
    }
}