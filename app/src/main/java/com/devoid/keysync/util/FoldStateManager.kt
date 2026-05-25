package com.devoid.keysync.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoldStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val windowInfoTracker = WindowInfoTracker.getOrCreate(context)

    val windowLayoutInfo: Flow<WindowLayoutInfo> = windowInfoTracker.windowLayoutInfo(context)

    val foldState: Flow<FoldState> = windowLayoutInfo.map { layoutInfo ->
        val foldingFeature = layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()
        when {
            foldingFeature == null -> FoldState.FLAT
            foldingFeature.state == FoldingFeature.State.FLAT -> FoldState.FLAT
            foldingFeature.state == FoldingFeature.State.HALF_OPENED -> FoldState.HALF_OPENED
            else -> FoldState.FLAT
        }
    }

    val hingeBounds: Flow<Rect?> = windowLayoutInfo.map { layoutInfo ->
        layoutInfo.displayFeatures
            .filterIsInstance<FoldingFeature>()
            .firstOrNull()
            ?.bounds
    }

    val windowSizeClass: Flow<WindowSizeClass> = windowLayoutInfo.map {
        val config = context.resources.configuration
        compute(config.screenWidthDp, config.screenHeightDp)
    }

    enum class FoldState { FLAT, HALF_OPENED }

    enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

    companion object {
        fun compute(config: Configuration): WindowSizeClass {
            return compute(config.screenWidthDp, config.screenHeightDp)
        }

        fun compute(widthDp: Int, heightDp: Int): WindowSizeClass {
            return when {
                widthDp < 600 -> WindowSizeClass.COMPACT
                widthDp < 840 -> WindowSizeClass.MEDIUM
                else -> WindowSizeClass.EXPANDED
            }
        }
    }
}
