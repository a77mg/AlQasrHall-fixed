package com.alqasrhall.booking.data

import android.content.Context
import androidx.room.Room

object AppContainer {
    private var database: AppDatabase? = null
    private var repository: Repository? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "qasr_al_daeery_db"
            )
            .fallbackToDestructiveMigration() // safe for rapid prototyping and initial deployments
            .build()
            database = db
            db
        }
    }

    fun getRepository(context: Context): Repository {
        return repository ?: synchronized(this) {
            val repo = Repository(getDatabase(context))
            repository = repo
            repo
        }
    }
}
