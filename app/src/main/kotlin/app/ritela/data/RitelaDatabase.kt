package app.ritela.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PeriodEntity::class], version = 1, exportSchema = true)
abstract class RitelaDatabase : RoomDatabase() {
    abstract fun periods(): PeriodDao
}
