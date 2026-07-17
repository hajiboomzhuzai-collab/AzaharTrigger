package org.citra.citra_emu.features.settings.model.view

data class TouchBinding(
    val keyCode: Int = -1,
    val axis: Int = -1,
    val positive: Boolean = true,
    val analog: Boolean = false,
    val threshold: Float = 0.5f,
    val x: Float,
    val y: Float
)
