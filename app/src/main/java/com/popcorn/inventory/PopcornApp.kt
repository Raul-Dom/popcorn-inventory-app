package com.popcorn.inventory

import android.app.Application
import com.popcorn.inventory.data.PopcornDatabase
import com.popcorn.inventory.data.PopcornRepository

class PopcornApp : Application() {
    val database by lazy { PopcornDatabase.create(this) }
    val repository by lazy { PopcornRepository(database) }
}
