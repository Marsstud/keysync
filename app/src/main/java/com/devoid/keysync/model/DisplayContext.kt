package com.devoid.keysync.model

import android.content.res.Resources
import androidx.compose.ui.geometry.Offset

data class DisplayContext(
    val widthPixels: Float,
    val heightPixels: Float
) {
    companion object {
        fun fromResources(resources: Resources): DisplayContext {
            val dm = resources.displayMetrics
            return DisplayContext(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
        }
    }
}

fun List<DraggableItem>.toRelative(displayContext: DisplayContext): List<DraggableItem> {
    return map { it.toRelative(displayContext) }
}

fun List<DraggableItem>.toAbsolute(displayContext: DisplayContext): List<DraggableItem> {
    return map { it.toAbsolute(displayContext) }
}

fun DraggableItem.toRelative(displayContext: DisplayContext): DraggableItem {
    val rel = { offset: Offset ->
        Offset(
            offset.x / displayContext.widthPixels,
            offset.y / displayContext.heightPixels
        )
    }
    return when (this) {
        is DraggableItem.VariableKey -> copy(position = rel(position))
        is DraggableItem.WASDGroup -> copy(
            position = rel(position),
            center = rel(center),
            w = rel(w),
            a = rel(a),
            s = rel(s),
            d = rel(d)
        )
        is DraggableItem.FixedKey -> copy(position = rel(position))
        is DraggableItem.CancelableKey -> copy(
            position = rel(position),
            cancelPosition = rel(cancelPosition)
        )
    }
}

fun DraggableItem.toAbsolute(displayContext: DisplayContext): DraggableItem {
    val abs = { offset: Offset ->
        Offset(
            offset.x * displayContext.widthPixels,
            offset.y * displayContext.heightPixels
        )
    }
    return when (this) {
        is DraggableItem.VariableKey -> copy(position = abs(position))
        is DraggableItem.WASDGroup -> copy(
            position = abs(position),
            center = abs(center),
            w = abs(w),
            a = abs(a),
            s = abs(s),
            d = abs(d)
        )
        is DraggableItem.FixedKey -> copy(position = abs(position))
        is DraggableItem.CancelableKey -> copy(
            position = abs(position),
            cancelPosition = abs(cancelPosition)
        )
    }
}

fun Offset.isAbsolute(displayContext: DisplayContext): Boolean {
    return x > 1f || y > 1f
}
