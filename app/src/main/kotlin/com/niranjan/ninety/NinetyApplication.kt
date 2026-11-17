package com.niranjan.ninety

import android.app.Application
import com.niranjan.ninety.data.NinetyDatabase
import com.niranjan.ninety.data.NinetyRepository
import com.niranjan.ninety.data.RoomNinetyRepository
import com.niranjan.ninety.platform.ReminderScheduler
import java.time.Clock

/** Small manual container; dependencies are constructed once and injected by constructor. */
class AppContainer(context: android.content.Context,
    val database: NinetyDatabase = NinetyDatabase.create(context),
    val clock: Clock = Clock.systemUTC(),
) {
    val repository: NinetyRepository = RoomNinetyRepository(database, clock)
    val reminders: ReminderScheduler = ReminderScheduler(context, repository)
}

open class NinetyApplication : Application() {
    open val container: AppContainer by lazy { AppContainer(this) }
}
