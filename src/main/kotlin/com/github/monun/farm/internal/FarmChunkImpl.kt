package com.github.monun.farm.internal

import com.github.monun.farm.FarmChunk
import com.github.monun.farm.util.UpstreamReference
import org.bukkit.Chunk
import java.util.*

class FarmChunkImpl(
    world: FarmWorldImpl,
    override val x: Int,
    override val z: Int
) : FarmChunk {
    companion object {
        const val DEFAULT_CAPACITY = 0xFF
        const val MAX_CAPACITY = 0xFFFF
    }

    private val worldRef = UpstreamReference(world)

    override val world: FarmWorldImpl
        get() = worldRef.get()

    override var bukkitChunk: Chunk? = null
        internal set

    private var _crops: Array<FarmCropImpl?> = arrayOfNulls(DEFAULT_CAPACITY)

    val crops: Array<FarmCropImpl>
        get() {
            @Suppress("UNCHECKED_CAST")
            return _crops.copyOf(size) as Array<FarmCropImpl>
        }

    private var size: Int = 0

    override val isLoaded: Boolean
        get() {
            return bukkitChunk != null
        }

    internal fun addCrop(crop: FarmCropImpl): FarmCropImpl? {
        val index = _crops.binaryIndexSearch(crop.index, 0, size)

        if (index >= 0) { // change
            return _crops.let { crops ->
                val oldCrop = crops[index]
                crops[index] = crop

                oldCrop
            }
        }

        if (++size >= _crops.size) { //배열 확장
            ensureCapacity()
        }

        val crops = _crops
        val insertionIndex = -index - 1
        System.arraycopy(crops, insertionIndex, crops, insertionIndex + 1, size - insertionIndex - 1)
        crops[insertionIndex] = crop

        return null
    }

    private fun ensureCapacity() {
        val crops = _crops
        val size = crops.size
        require(size < MAX_CAPACITY) { "Overflow" }

        this._crops = crops.copyOf(size shl 1)
    }

    internal fun removeCrop(crop: FarmCropImpl) {
        val size = this.size
        val index = _crops.binaryIndexSearch(crop.index, 0, size)

        require(index >= 0) { "Unregistered crop ${crop.world.name} ${crop.x} ${crop.y} ${crop.z}" }
        require(_crops[index] === crop) { "Crop mismatch ${crop.world.name} ${crop.x} ${crop.y} ${crop.z}" }
        shrink(index)
    }

    internal fun removeCrop(x: Int, y: Int, z: Int): FarmCropImpl? {
        val key = indexOf(x, y, z)
        val index = _crops.binaryIndexSearch(key, 0, size)

        if (index >= 0) {
            val crop = _crops[index]
            shrink(index)
            return crop
        }

        return null
    }

    private fun shrink(index: Int) {
        val crops = _crops
        val numMoved = size - index - 1

        if (numMoved > 0) {
            crops.copyInto(crops, index, index + 1, size)
        }

        crops[--size] = null

    }

    override fun cropAt(x: Int, y: Int, z: Int): FarmCropImpl? {
        val crops = this._crops
        val key = indexOf(x, y, z)
        val index = crops.binaryIndexSearch(key, 0, size)

        return if (index >= 0) crops[index] else null
    }

    internal fun destroy() {
        worldRef.clear()
        bukkitChunk = null
        Arrays.fill(_crops, null)
        size = 0
    }
}


fun pair(x: Int, z: Int): Long {
    return (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)
}

fun least(pair: Long): Int {
    return (pair and 0xFFFFFFFFL).toInt()
}

fun most(pair: Long): Int {
    return (pair ushr 32 and 0xFFFFFFFFL).toInt()
}