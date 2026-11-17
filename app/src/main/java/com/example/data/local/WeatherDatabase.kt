package com.example.data.local

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
import com.example.data.model.SavedCity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_cities")
data class SavedCityEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val nameAr: String,
    val countryEn: String,
    val countryAr: String,
    val admin1: String?,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean,
    val lastTempC: Double?,
    val lastWeatherCode: Int?,
    val lastIsDay: Boolean?,
    val lastHighC: Double?,
    val lastLowC: Double?,
    val updatedAt: Long
) {
    fun toDomain(): SavedCity = SavedCity(
        id = id,
        nameEn = nameEn,
        nameAr = nameAr,
        countryEn = countryEn,
        countryAr = countryAr,
        admin1 = admin1,
        latitude = latitude,
        longitude = longitude,
        isCurrentLocation = isCurrentLocation,
        lastTempC = lastTempC,
        lastWeatherCode = lastWeatherCode,
        lastIsDay = lastIsDay,
        lastHighC = lastHighC,
        lastLowC = lastLowC,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(city: SavedCity): SavedCityEntity = SavedCityEntity(
            id = city.id,
            nameEn = city.nameEn,
            nameAr = city.nameAr,
            countryEn = city.countryEn,
            countryAr = city.countryAr,
            admin1 = city.admin1,
            latitude = city.latitude,
            longitude = city.longitude,
            isCurrentLocation = city.isCurrentLocation,
            lastTempC = city.lastTempC,
            lastWeatherCode = city.lastWeatherCode,
            lastIsDay = city.lastIsDay,
            lastHighC = city.lastHighC,
            lastLowC = city.lastLowC,
            updatedAt = city.updatedAt
        )
    }
}

@Entity(tableName = "cached_forecasts")
data class CachedForecastEntity(
    @PrimaryKey val cityId: String,
    val forecastJson: String,
    val fetchedAt: Long
)

@Dao
interface SavedCityDao {
    @Query("SELECT * FROM saved_cities ORDER BY isCurrentLocation DESC, updatedAt DESC")
    fun observeCities(): Flow<List<SavedCityEntity>>

    @Query("SELECT * FROM saved_cities ORDER BY isCurrentLocation DESC, updatedAt DESC")
    suspend fun getCitiesOnce(): List<SavedCityEntity>

    @Query("SELECT * FROM saved_cities WHERE id = :id LIMIT 1")
    suspend fun getCityById(id: String): SavedCityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCity(city: SavedCityEntity)

    @Query(
        "UPDATE saved_cities SET lastTempC = :tempC, lastWeatherCode = :weatherCode, " +
            "lastIsDay = :isDay, lastHighC = :highC, lastLowC = :lowC, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateCityWeatherSummary(
        id: String,
        tempC: Double,
        weatherCode: Int,
        isDay: Boolean,
        highC: Double?,
        lowC: Double?,
        updatedAt: Long
    )

    @Query("DELETE FROM saved_cities WHERE id = :id")
    suspend fun deleteCityById(id: String)
}

@Dao
interface CachedForecastDao {
    @Query("SELECT * FROM cached_forecasts WHERE cityId = :cityId LIMIT 1")
    suspend fun getForecast(cityId: String): CachedForecastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForecast(entity: CachedForecastEntity)

    @Query("DELETE FROM cached_forecasts WHERE cityId = :cityId")
    suspend fun deleteForecast(cityId: String)
}

@Database(
    entities = [SavedCityEntity::class, CachedForecastEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun savedCityDao(): SavedCityDao
    abstract fun cachedForecastDao(): CachedForecastDao

    companion object {
        @Volatile
        private var INSTANCE: WeatherDatabase? = null

        fun getInstance(context: Context): WeatherDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WeatherDatabase::class.java,
                    "skypulse_weather.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
