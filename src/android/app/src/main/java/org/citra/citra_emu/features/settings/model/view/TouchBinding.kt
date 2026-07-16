package org.citra.citra_emu.features.settings.model.view

data class TouchBinding(
    val keyCode: Int,
    val axis: Int = -1,
    val x: Float,
    val y: Float
)
