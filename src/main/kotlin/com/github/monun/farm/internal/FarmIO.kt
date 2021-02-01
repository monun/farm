package com.github.monun.farm.internal

import org.bukkit.plugin.Plugin
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.*


interface FarmIO {
    fun saveWorld(world: FarmWorldImpl)

    fun saveCrop(crop: FarmCropImpl)

    fun deleteCrop(crop: FarmCropImpl)

    fun loadChunk(chunk: FarmChunkImpl): List<FarmCropImpl>

    fun commit()

    fun close()
}

class FarmIOSQLite(private val plugin: Plugin, file: File) : FarmIO {
    private val conn: Connection
    private val saveWorld: PreparedStatement
    private val loadWorld: PreparedStatement
    private val saveCrops: PreparedStatement
    private val loadCrops: PreparedStatement
    private val deleteCrops: PreparedStatement
    private val clearSQL: String

    private val idsByWorld = WeakHashMap<FarmWorldImpl, Int>()

    init {
        Class.forName("org.sqlite.JDBC")
        file.parentFile.mkdirs()
        this.conn = DriverManager.getConnection("jdbc:sqlite:${file.path}")

        createTables()

        saveWorld = createSQL("save-world")
        loadWorld = createSQL("load-world")
        saveCrops = createSQL("save-crops")
        loadCrops = createSQL("load-crops")
        deleteCrops = createSQL("delete-crops")
        clearSQL = loadSql("clear-crops")

        conn.autoCommit = false
    }

    private fun createTables() {
        conn.createStatement().useAutoClose { stmt ->
            loadSqlList("create-tables").forEach { sql ->
                stmt.execute(sql)
            }
        }
    }

    private fun createSQL(name: String): PreparedStatement {
        return conn.prepareStatement(loadSql(name))
    }

    private fun loadSql(name: String): String {
        return loadSqlList(name)[0]
    }

    private fun loadSqlList(name: String): List<String> {
        val input = requireNotNull(plugin.getResource("sql/sqlite/$name.sql")) { "Not found sql file $name" }

        input.bufferedReader().useLines { sequence ->
            val builder = StringBuilder()

            sequence.forEach {
                if (builder.isNotEmpty()) builder.append("\n")
                builder.append(it)
            }

            return builder.split(";".toRegex()).asSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    override fun saveWorld(world: FarmWorldImpl) {
        if (world in idsByWorld) return

        val name = world.name

        saveWorld.runCatching {
            runCatching {
                setString(1, name)
                executeUpdate()
            }.onFailure { exception: Throwable ->
                exception.printStackTrace()
            }
            clearParameters()
        }

        loadWorld.runCatching {
            runCatching {
                setString(1, name)
                executeQuery().useAutoClose { resultSet ->
                    require(resultSet.next()) { "Not found world id in database for $name" }

                    idsByWorld[world] = resultSet.getInt("id")
                }
            }.onFailure { exception: Throwable ->
                exception.printStackTrace()
            }
            runCatching { clearParameters() }
        }
    }

    private val FarmWorldImpl.id: Int
        get() {
            return requireNotNull(idsByWorld[this]) { "Not found id for $name" }
        }

    override fun saveCrop(crop: FarmCropImpl) {
        saveCrops.runCatching {
            runCatching { //INSERT OR REPLACE INTO crops (world_id, x, y, z, planted_time) VALUES (?, ?, ?, ?, ?);
                setInt(1, crop.world.id)
                setInt(2, crop.x)
                setInt(3, crop.y)
                setInt(4, crop.z)
                setLong(5, crop.plantedTime)
                executeUpdate()
            }.onFailure { exception ->
                exception.printStackTrace()
            }
            clearParameters()
        }
    }

    override fun deleteCrop(crop: FarmCropImpl) {
        deleteCrops.runCatching {
            runCatching { //UPDATE crops SET deleted=? WHERE world_id=? AND x=? AND y=? AND z=?;
                setInt(1, crop.world.id)
                setInt(2, crop.x)
                setInt(3, crop.y)
                setInt(4, crop.z)
                executeUpdate()
            }.onFailure { exception -> exception.printStackTrace() }
            clearParameters()
        }
    }

    override fun loadChunk(chunk: FarmChunkImpl): List<FarmCropImpl> {
        val minX = chunk.x shl 4
        val maxX = minX + 0xF
        val minZ = chunk.z shl 4
        val maxZ = minZ + 0xF

        val list = arrayListOf<FarmCropImpl>()

        loadCrops.runCatching { //SELECT x, y, z, planted_time FROM crops WHERE world_id=? AND x>=? AND x<=? AND z>=? AND z<=? AND deleted=false;
            runCatching {
                setInt(1, chunk.world.id)
                setInt(2, minX)
                setInt(3, maxX)
                setInt(4, minZ)
                setInt(5, maxZ)

                executeQuery().useAutoClose { resultSet ->
                    while (resultSet.next()) {
                        val x = resultSet.getInt("x")
                        val y = resultSet.getInt("y")
                        val z = resultSet.getInt("z")
                        val plantedTime = resultSet.getLong("planted_time")

                        list += FarmCropImpl(chunk, x, y, z, plantedTime)
                    }
                }
            }.onFailure { exception -> exception.printStackTrace() }

            clearParameters()
        }

        return list
    }

    override fun commit() {
        conn.commit()
    }

    override fun close() {
        commit()
        conn.runCatching {
            createStatement().useAutoClose { stmt ->
                stmt.execute(clearSQL)
            }
            commit()
            close()
        }.onFailure { exception -> exception.printStackTrace() }
    }
}

private fun <T: AutoCloseable> T.useAutoClose(block: (T) -> Unit) {
    try {
        block.invoke(this)
    } finally {
        close()
    }
}