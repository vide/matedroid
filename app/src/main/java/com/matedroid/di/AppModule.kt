package com.matedroid.di

import android.content.Context
import com.matedroid.data.local.SettingsDataStore
import com.matedroid.notification.ChargingNotificationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideChargingNotificationManager(
        @ApplicationContext context: Context
    ): ChargingNotificationManager {
        return ChargingNotificationManager(context)
    }
}
