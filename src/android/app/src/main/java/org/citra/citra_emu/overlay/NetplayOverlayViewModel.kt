package org.citra.citra_emu.overlay

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetplayOverlayViewModel : ViewModel() {

    private val _messages =
        MutableStateFlow<List<Pair<Int, String>>>(emptyList())

    val messages: StateFlow<List<Pair<Int, String>>> = _messages

    private val _connected =
        MutableStateFlow(false)

    val connected: StateFlow<Boolean> = _connected

    fun addMessage(type: Int, message: String) {
        _messages.value = _messages.value + (type to message)
    }

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    fun clear() {
        _messages.value = emptyList()
    }
}
