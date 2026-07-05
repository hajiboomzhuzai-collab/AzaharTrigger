package org.citra.citra_emu.overlay

data class ChatMessage(
    val type: Int,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
