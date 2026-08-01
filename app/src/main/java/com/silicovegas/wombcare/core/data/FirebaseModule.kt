package com.silicovegas.wombcare.core.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideDatabase(): FirebaseDatabase = Firebase.database.apply {
        // Survive a monitoring session across a brief network drop: queued writes replay on
        // reconnect. Bounded so it can never grow without limit on a long-lived install.
        setPersistenceEnabled(true)
    }
}
