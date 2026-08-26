package com.exapps.velox.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.exapps.velox.MainActivity
import com.exapps.velox.core.domain.player.PlayerController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Phase 1 M4 "Widgets": the home-screen now-playing glance widget. The same-process
 * [PlayerController] state drives it reactively — Glance recomposes and pushes new
 * RemoteViews whenever the collected StateFlow emits. Transport buttons run through
 * [ActionCallback]s that reach the same singleton via Hilt's entry point.
 *
 * Deliberately minimal for v1: text + transport controls. Artwork rendering needs a
 * bitmap pipeline (ImageProvider(bitmap)) with caching — deferred; see PROGRESS.md.
 */
class VeloxAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VeloxAppWidget
}

/** Hilt bridge for widget/action contexts (receivers can't field-inject cleanly). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun playerController(): PlayerController
}

private fun controller(context: Context): PlayerController =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        .playerController()

object VeloxAppWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val player = controller(context)

        provideContent {
            val state by player.state.collectAsState()

            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp),
                ) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .clickable(OpenAppAction.action),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight().padding(start = 14.dp)) {
                            Text(
                                text = state.currentItem?.title ?: "Velox",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = state.currentItem?.artistName ?: "",
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                                maxLines = 1,
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        GlyphButton("\u23EE", context.getString(com.exapps.velox.R.string.cd_previous), actionRunCallback<PreviousAction>())
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        GlyphButton(
                            if (state.isPlaying) "\u23F8" else "\u25B6",
                            context.getString(if (state.isPlaying) com.exapps.velox.R.string.cd_pause else com.exapps.velox.R.string.cd_play),
                            actionRunCallback<PlayPauseAction>(),
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        GlyphButton("\u23ED", context.getString(com.exapps.velox.R.string.cd_next), actionRunCallback<NextAction>())
                        Spacer(modifier = GlanceModifier.width(10.dp))
                    }
                }
            }
        }
    }

    /** Round translucent glyph button — unicode glyphs avoid shipping extra drawables.
     * L9 (app-shell review): the glyph itself is meaningless to TalkBack, so the box
     * carries a real contentDescription. */
    @androidx.compose.runtime.Composable
    private fun GlyphButton(glyph: String, contentDescription: String, onClick: Action) {
        Box(
            modifier = GlanceModifier
                .width(40.dp)
                .height(40.dp)
                .background(ColorProvider(Color(0x22FFFFFF)))
                .cornerRadius(20.dp)
                .clickable(onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp))
        }
    }

    /** Play/pause toggle from the widget surface. */
    class PlayPauseAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            controller(context).playPause()
        }
    }

    /** Skip to next queue item. */
    class NextAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            controller(context).skipNext()
        }
    }

    /** Skip to previous queue item. */
    class PreviousAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            controller(context).skipPrevious()
        }
    }

    /** Opens MainActivity (singleTop — no duplicate stacks). */
    class OpenAppAction : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        companion object {
            val action: Action = actionRunCallback<OpenAppAction>()
        }
    }
}
