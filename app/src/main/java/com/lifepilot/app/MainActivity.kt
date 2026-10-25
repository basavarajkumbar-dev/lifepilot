package com.lifepilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

// Temporary dependency-resolution probe; replaced by the real app.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("LifePilot") }
    }
}

@Entity
data class ProbeEntity(@PrimaryKey val id: Long)

@Dao
interface ProbeDao {
    @Query("SELECT * FROM ProbeEntity")
    suspend fun all(): List<ProbeEntity>
}

@Database(entities = [ProbeEntity::class], version = 1)
abstract class ProbeDb : RoomDatabase() {
    abstract fun dao(): ProbeDao
}
