package app.ritela

import android.app.Application
import androidx.room.Room
import app.ritela.data.PeriodRepository
import app.ritela.data.RitelaDatabase

class RitelaApplication : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, RitelaDatabase::class.java, "ritela.db").build()
    }
    val periods by lazy { PeriodRepository(database.periods()) }
}
