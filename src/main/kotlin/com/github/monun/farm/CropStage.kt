package com.github.monun.farm

import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional

interface CropStage {
    val isResult: Boolean
        get() = false

    fun update(block: Block, prevBlockData: BlockData)

    companion object {
        fun growth(blockData: BlockData): Growth {
            return Growth(blockData)
        }

        fun result(blockData: BlockData): Result {
            return ResultBlock(blockData)
        }

        fun result(action: (block: Block, newBlockData: BlockData) -> Unit): Result {
            return ResultAction(action)
        }
    }

    interface Result : CropStage {
        override val isResult: Boolean
            get() = true
    }

    class Growth internal constructor(
        val blockData: BlockData
    ) : CropStage {
        var duration: Long = 0L
            internal set

        var time: Long = 0L
            internal set

        lateinit var nextStage: CropStage
            internal set

        override fun update(block: Block, prevBlockData: BlockData) {
            block.update(prevBlockData, blockData)
        }
    }

    private class ResultBlock(
        val blockData: BlockData
    ) : Result {
        override fun update(block: Block, prevBlockData: BlockData) {
            block.update(prevBlockData, blockData)
        }
    }

    private class ResultAction(
        private val action: (block: Block, prevBlockData: BlockData) -> Unit
    ) : Result {
        override fun update(block: Block, prevBlockData: BlockData) {
            action(block, prevBlockData)
        }
    }
}

private fun Block.update(prevBlockData: BlockData, newBlockData: BlockData) {
    if (newBlockData is Directional && prevBlockData is Directional) {
        blockData = (newBlockData.clone() as Directional).apply {
            facing = prevBlockData.facing
        }
    } else {
        blockData = newBlockData
    }
}