package com.exapps.velox.core.audioanalysis.di

import com.exapps.velox.core.audioanalysis.DefaultTrackAnalysisService
import com.exapps.velox.core.audioanalysis.DefaultTrackAnalyzer
import com.exapps.velox.core.domain.audio.TrackAnalysisService
import com.exapps.velox.core.domain.audio.TrackAnalyzer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 3 / Wave 3 / Round 2 — Hilt wiring for the
 * `:core:audio-analysis` module. Binds the analyzer and the
 * service ports to their default implementations; consumers
 * (the player stack, the Now Playing screen) inject the port,
 * not the concrete class.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioAnalysisModule {

    @Binds
    @Singleton
    abstract fun bindTrackAnalyzer(impl: DefaultTrackAnalyzer): TrackAnalyzer

    @Binds
    @Singleton
    abstract fun bindTrackAnalysisService(impl: DefaultTrackAnalysisService): TrackAnalysisService
}
