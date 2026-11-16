package com.htoc.banyuubox.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** 一台 DNS 服务器，主键就是地址（如 223.5.5.5）。 */
@Entity(tableName = "dns_servers")
data class DnsServer(
    @PrimaryKey val address: String,
)

@Dao
abstract class DnsServerDao {

    @Query("SELECT * FROM dns_servers ORDER BY address ASC")
    abstract suspend fun getAll(): List<DnsServer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(servers: List<DnsServer>)

    @Query("DELETE FROM dns_servers")
    abstract suspend fun clear()

    /** 用一份新列表整体替换旧数据（先清空再插入，事务保证原子性）。 */
    @Transaction
    open suspend fun replaceAll(addresses: List<String>) {
        clear()
        insertAll(addresses.map { DnsServer(it) })
    }
}

@Database(entities = [DnsServer::class], version = 1, exportSchema = false)
abstract class DnsDatabase : RoomDatabase() {

    abstract fun dnsServerDao(): DnsServerDao

    companion object {
        @Volatile
        private var instance: DnsDatabase? = null

        fun get(context: Context): DnsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DnsDatabase::class.java,
                    "banyuubox.db",
                ).build().also { instance = it }
            }
    }
}
