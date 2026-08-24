package com.exapps.velox.player.engine.di

import com.exapps.velox.core.domain.player.AudioEffectsController
import com.exapps.velox.player.engine.AndroidAudioEffectsController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the audiofx-backed implementation behind the domain seam — the equalizer
 * feature injects [AudioEffectsController] and never learns media3/audiofx exist. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioEffectsModule {

    @Binds
    @Singleton
    abstract fun bindAudioEffectsController(impl: AndroidAudioEffectsController): AudioEffectsController
}
