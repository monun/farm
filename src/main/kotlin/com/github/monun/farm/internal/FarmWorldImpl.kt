package com.github.monun.farm.internal

import com.github.monun.farm.CropType
import com.github.monun.farm.FarmWorld
import com.google.common.collect.ImmutableList
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import org.bukkit.Chunk
import org.bukkit.World

class FarmWorldImpl(
    override val name: String,
    override val bukkitWorld: World
) : FarmWorld {
    private val chunksByHash = Long2ObjectOpenHashMap<FarmChunkImpl>()

    private val unloadQueue = LongOpenHashSet()

    override val loadedChunks: List<FarmChunkImpl>
        get() = ImmutableList.copyOf(chunksByHash.values)

    override fun chunkAt(x: Int, z: Int): FarmChunkImpl? {
        return chunksByHash[pair(x, z)]
    }

    override fun cropAt(x: Int, y: Int, z: Int): FarmCropImpl? {
        return chunkAt(x shr 4, z shr 4)?.cropAt(x, y, z)
    }

    private fun getOrLoadChunk(x: Int, z: Int, bukkitChunk: Chunk? = null): FarmChunkImpl {
        val key = pair(x, z)
        var farmChunk = chunksByHash[key]

        if (farmChunk == null) {
            farmChunk = FarmChunkImpl(this, x, z)
            chunksByHash[key] = farmChunk

            val crops = FarmInternal.io.loadChunk(farmChunk)

            if (bukkitChunk == null) { // 강제 불러오기
                for (crop in crops) {
                    farmChunk.addCrop(crop)
                }
                unloadQueue += key //비대칭 청크 상태이므로 언로드 큐에 등록
            } else { // 청크 로딩과 함께 불러오기
                farmChunk.bukkitChunk = bukkitChunk
                val currentTime = System.currentTimeMillis()

                for (crop in crops) {
                    val result = crop.update(currentTime)

                    if (result == FarmCropImpl.UpdateResult.SUCCESS_GROWTH) {//작물 성장이 남아있을경우
                        farmChunk.addCrop(crop)
                        FarmInternal.task.registerCrop(crop)
                    } else {
                        FarmInternal.io.deleteCrop(crop) //DB 제거
                    }
                }
            }

            return farmChunk
        }

        if (bukkitChunk != null && bukkitChunk !== farmChunk.bukkitChunk) {
            farmChunk.bukkitChunk = bukkitChunk
            unloadQueue -= key //버킷 청크와 대칭상태일때 언로드 큐에서 제거

            val currentTime = System.currentTimeMillis()

            farmChunk.crops.forEach { crop ->
                if (!crop.isQueued) { //큐에 등록되지 않은 작물 (업데이트 할 때 청크 언로딩된 작물 혹은 비대칭 로딩되었던 청크
                    val result = crop.update(currentTime)

                    if (result == FarmCropImpl.UpdateResult.SUCCESS_GROWTH) {
                        FarmInternal.task.registerCrop(crop)
                    } else { //업데이트 종료
                        farmChunk.removeCrop(crop)
                        FarmInternal.io.deleteCrop(crop) //DB 제거
                    }
                }
            }
        }

        return farmChunk
    }

    internal fun loadChunk(bukkitChunk: Chunk): FarmChunkImpl {
        return getOrLoadChunk(bukkitChunk.x, bukkitChunk.z, bukkitChunk)
//        val x = bukkitChunk.x
//        val z = bukkitChunk.z
//        val key = pair(x, z)
//        var farmChunk = chunksByHash[key]
//
//        if (farmChunk == null) {
//            farmChunk = FarmChunkImpl(this, x, z).apply {
//                this.bukkitChunk = bukkitChunk
//            }
//            chunksByHash[key] = farmChunk
//
//            val time = System.currentTimeMillis()
//
//            FarmInternal.io.loadChunk(farmChunk).forEach { crop ->
//                val result = crop.update(time)
//
//                if (result == FarmCropImpl.UpdateResult.SUCCESS_GROWTH) {
//                    farmChunk.addCrop(crop)
//                } else {
//                    FarmInternal.io.deleteCrop(crop)
//                }
//            }
//        } else {
//            farmChunk.bukkitChunk = bukkitChunk
//            unloadQueue.remove(key)
//        }
//
//        val time = System.currentTimeMillis()
//
//        farmChunk.crops.forEach { crop ->
//            if (!crop.isQueued) {
//                val result = crop.update(time)
//
//                if (result == FarmCropImpl.UpdateResult.SUCCESS_GROWTH) {
//                    FarmInternal.task.registerCrop(crop)
//                } else {
//                    farmChunk.removeCrop(crop)
//                }
//            }
//        }
//
//        return farmChunk
    }

    internal fun unloadChunk(x: Int, z: Int) {
        val key = pair(x, z)
        val farmChunk = chunksByHash[key]

        if (farmChunk != null && farmChunk.isLoaded) {
            farmChunk.bukkitChunk = null
            unloadQueue += key
        }
    }

    internal fun unloadChunks() {
        val chunksByHash = this.chunksByHash
        val unloadQueue = unloadQueue

        if (unloadQueue.isNotEmpty()) {
            val iterator = unloadQueue.iterator()

            while (iterator.hasNext()) {
                val key = iterator.nextLong()
                val chunk = chunksByHash.remove(key)

                FarmInternal.task.run {
                    chunk.crops.forEach { crop ->
                        unregisterCrop(crop)
                    }
                }

                chunk.destroy()
            }
        }

        unloadQueue.clear()
    }

    internal fun addCrop(x: Int, y: Int, z: Int, type: CropType) {
        val chunkX = x shr 4
        val chunkZ = z shr 4

        val chunk = getOrLoadChunk(chunkX, chunkZ)

        val crop = FarmCropImpl(chunk, x, y, z, System.currentTimeMillis())
        crop.updateNextGrowthTime(type.firstStage)
        chunk.addCrop(crop)?.let { oldCrop ->
            if (oldCrop.isQueued) {
                FarmInternal.task.unregisterCrop(oldCrop)
            }
        }

        if (chunk.isLoaded) {
            FarmInternal.task.registerCrop(crop)
        }
        FarmInternal.io.saveCrop(crop)
    }

    internal fun removeCrop(x: Int, y: Int, z: Int) {
        val chunkX = x shr 4
        val chunkZ = z shr 4

        chunkAt(chunkX, chunkZ)?.let { chunk ->
            val crop = chunk.removeCrop(x, y, z)

            if (crop != null) {
                if (crop.isQueued)
                    FarmInternal.task.unregisterCrop(crop)

                FarmInternal.io.deleteCrop(crop)
            }
        }
    }

    internal fun destroy() {
        for (chunk in chunksByHash.values) {
            chunk.destroy()
        }
        chunksByHash.clear()
    }

}