package com.github.monun.farm.internal.timer

import com.github.monun.farm.internal.FarmCropImpl
import com.github.monun.tap.fake.FakeEntity
import com.github.monun.tap.protocol.Packet
import com.github.monun.tap.protocol.PacketSupport
import com.github.monun.tap.protocol.sendServerPacket
import org.bukkit.Bukkit
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.regex.Pattern
import kotlin.math.max

internal class CropTimeViewer(
    private val player: Player,
    private val fakeStand: FakeEntity,
    private val crop: FarmCropImpl
) {
    private var ticks = 0

    init {
        update(System.currentTimeMillis())
        updatePosition()
    }

    fun show() {
        fakeStand.isVisible = true
    }

    private fun updatePosition() {
        fakeStand.moveTo(crop.block.location.apply {
            add(0.5, 0.75, 0.5)
        })
    }

    private fun isWatching(): Boolean {
        val block = player.getTargetBlockExact(5)

        return (block != null && block.x == crop.x && block.y == crop.y && block.z == crop.z)
    }

    fun update(currentTime: Long): Boolean {
        val crop = crop
        if (!crop.isQueued || !crop.chunk.isLoaded) return false
        if (!isWatching()) return false

        val type = crop.type ?: return false
        val resultTime = crop.plantedTime + type.duration
        val remainTime = max(0L, resultTime - currentTime)
        val display = TimeFormat.format(remainTime + 999)

        if (++ticks == 2) {
            fakeStand.updateMetadata<ArmorStand> {
                isCustomNameVisible = true
            }
        }

        fakeStand.updateMetadata<ArmorStand> {
            customName = display
        }

        return true
    }

    fun remove() {
        fakeStand.remove()
    }
}

enum class TimeFormat(
    val displayName: String,
    val millis: Long,
    var child: TimeFormat? = null
) {
    SECOND("초", 1000L),
    MINUTE("분", SECOND.millis * 60L, SECOND),
    HOUR("시간", MINUTE.millis * 60L, MINUTE),
    DAY("일", HOUR.millis * 60L, HOUR);

    companion object {
        fun format(time: Long): String {
            val unit = getUnitByTime(time)
            val builder = StringBuilder()

            builder.append(time / unit.millis).append(unit.displayName)

            unit.child?.let { child ->
                val childTime = (time % unit.millis) / child.millis

                if (childTime > 0) {
                    builder.append(' ').append(childTime).append(child.displayName)
                }
            }

            return builder.toString()
        }

        private fun getUnitByTime(time: Long): TimeFormat {
            return when {
                time >= DAY.millis -> DAY
                time >= HOUR.millis -> HOUR
                time >= MINUTE.millis -> MINUTE
                else -> SECOND
            }
        }
    }
}



