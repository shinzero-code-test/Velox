package com.exapps.velox.core.common.util

/**
 * SCREENS_OVERVIEW.md §5 requires every major list/detail screen to define
 * Loading / Empty / Error / Content (+ Offline/no-permission) states. ViewModels
 * expose one of these instead of ad hoc nullable/boolean flag soup, and the
 * corresponding Compose screen does a single `when` over it.
 */
sealed interface ScreenState<out T> {
    data object Loading : ScreenState<Nothing>
    data class Content<T>(val data: T) : ScreenState<T>
    data object Empty : ScreenState<Nothing>
    data class Error(val message: String? = null) : ScreenState<Nothing>

    /** SCREENS_OVERVIEW.md §5: "Offline / No permission — Permission explanation + action button." */
    data class PermissionRequired(val rationale: String? = null) : ScreenState<Nothing>
}

inline fun <T> ScreenState<T>.dataOrNull(): T? = (this as? ScreenState.Content)?.data
