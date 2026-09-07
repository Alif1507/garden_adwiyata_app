package com.example.home_garden_system.garden

import android.content.Context
import com.example.home_garden_system.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object RepositoryFactory {
    private var backend: Pair<FirebaseAuth, FirebaseDatabase>? = null

    @Synchronized
    fun create(context: Context): GardenRepository {
        backend?.let { (auth, database) ->
            database.goOnline()
            return FirebaseGardenRepository(auth, database)
        }
        val app = FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL)
                .build())
        val auth = FirebaseAuth.getInstance(app)
        val database = FirebaseDatabase.getInstance(app)
        if (BuildConfig.FIREBASE_EMULATOR) {
            auth.useEmulator("10.0.2.2", 9099)
            database.useEmulator("10.0.2.2", 9000)
        }
        backend = auth to database
        // Firebase defaults to memory-only caching. Do not enable disk persistence for controls.
        return FirebaseGardenRepository(auth, database)
    }
}
