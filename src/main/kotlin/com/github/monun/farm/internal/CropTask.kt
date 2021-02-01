package com.github.monun.farm.internal

import com.github.monun.tap.fake.FakeEntityServer
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

internal class CropTask : Runnable {
    private val queueSlot = ArrayList<IndexedQueue<FarmCropImpl>>(256)

    private val leastQueue = PriorityQueue<IndexedQueue<FarmCropImpl>>(256) { o1, o2 ->
        o1.count().compareTo(o2.count())
    }

    var size: Int = 0
        private set

    private var ticks = 0

    private var partialIndex = 0.0

    private var partialIndexPerTick = 1.0

    internal lateinit var fakeEntityServer: FakeEntityServer

    init {
        createNewQueue()
    }

    private fun createNewQueue() {
        val queue = IndexedQueue<FarmCropImpl>(PriorityQueue(4096), queueSlot.count())
        queueSlot += queue
        leastQueue += queue
    }

    fun registerCrop(crop: FarmCropImpl) {
        ensureCapacity()

        val queue = leastQueue.remove()
        queue.offer(crop)
        crop.queueSlot = queue.index
        size++
        leastQueue.offer(queue)
    }

    fun unregisterCrop(crop: FarmCropImpl) {
        queueSlot[crop.queueSlot].run {
            remove(crop)
            updateLeastQueue(this)
            crop.queueSlot = -1
        }
        size--
    }

    private fun updateLeastQueue(queue: IndexedQueue<FarmCropImpl>) {
        leastQueue.run {
            remove(queue)
            offer(queue)
        }
    }

    private fun ensureCapacity() {
        val growSize = size + 1
        val newSlotSize = ceil(sqrt(growSize.toDouble()) / 4.0).toInt()
        val slot = queueSlot
        val slotSize = slot.size

        if (slotSize < newSlotSize) {
            for (i in slotSize until newSlotSize) {
                createNewQueue()
            }

            partialIndexPerTick = max(1.0, newSlotSize / 20.0)
        }
    }

    override fun run() {
        fakeEntityServer.update()

        if (++ticks % 20 * 60 * 5 == 0) {
            for (world in FarmInternal.manager.worlds) {
                world.unloadChunks()
            }
        }

        val fromIndex = partialIndex.toInt()
        partialIndex += partialIndexPerTick
        val toIndex = partialIndex.toInt()

        val time = System.currentTimeMillis()
        val slot = queueSlot
        val slotSize = slot.size

        for (i in fromIndex until toIndex) {
            val queue = slot[i % slotSize]

            var changed = false

            while (queue.isNotEmpty()) {
                val crop = queue.peek()

                if (time < crop.nextGrowthTime)
                    break

                queue.remove()

                val result = crop.update(time)

                if (result == FarmCropImpl.UpdateResult.SUCCESS_GROWTH) {
                    queue.offer(crop)
                    continue
                }

                if (result == FarmCropImpl.UpdateResult.SUCCESS_DONE
                    || result == FarmCropImpl.UpdateResult.FAIL_TYPE_MISMATCH
                ) {
                    crop.chunk.removeCrop(crop)
                    FarmInternal.io.deleteCrop(crop)
                }

                size--
                changed = true
            }

            if (changed)
                updateLeastQueue(queue)
        }

        if (partialIndex >= slotSize) {
            partialIndex %= 1.0
        }

        FarmInternal.io.commit()

        FarmInternal.manager.timeViewers.values.removeIf {
            if (!it.update(time)) {
                it.remove()
                true
            } else
                false
        }
    }

    internal class IndexedQueue<E>(
        private val queue: Queue<E>,
        val index: Int
    ) : Queue<E> by queue, Comparable<IndexedQueue<E>> {
        override fun compareTo(other: IndexedQueue<E>): Int {
            return queue.size.compareTo(other.queue.size)
        }
    }
}