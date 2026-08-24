package com.exapps.velox.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exapps.velox.core.domain.player.PlaybackState
import com.exapps.velox.core.domain.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playerController.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaybackState(),
    )

    fun onPlayPause() = playerController.playPause()
    fun onSkipNext() = playerController.skipNext()
}
