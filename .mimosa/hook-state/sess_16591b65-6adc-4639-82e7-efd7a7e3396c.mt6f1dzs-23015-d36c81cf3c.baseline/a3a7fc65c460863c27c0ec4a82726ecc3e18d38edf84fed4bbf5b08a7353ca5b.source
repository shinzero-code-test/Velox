package com.exapps.velox.player.service.di

import com.exapps.velox.core.domain.player.PlayerController
import com.exapps.velox.player.service.MediaControllerPlayerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerServiceModule {

    @Binds
    @Singleton
    abstract fun bindPlayerController(impl: MediaControllerPlayerController): PlayerController
}
