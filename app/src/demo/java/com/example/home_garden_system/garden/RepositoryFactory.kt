package com.example.home_garden_system.garden

import android.content.Context

object RepositoryFactory {
    fun create(context: Context): GardenRepository = DemoGardenRepository()
}
