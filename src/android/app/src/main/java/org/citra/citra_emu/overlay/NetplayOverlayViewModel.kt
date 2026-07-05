package org.citra.citra_emu.overlay

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetplayOverlayViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    fun addMessage(type: Int, msg: String) {
        _messages.value = _messages.value + (type to msg)
    }

    fun clear() {
        _messages.value = emptyList()
    }
}
