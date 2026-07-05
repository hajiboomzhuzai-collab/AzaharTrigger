package org.citra.citra_emu.overlay

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    class ChatViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val tv = TextView(parent.context)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        return ChatViewHolder(tv)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.textView.text = items[position].message
    }

    override fun getItemCount(): Int = items.size

    fun addMessage(msg: ChatMessage) {
        items.add(msg)

        // keep last 8 messages only
        if (items.size > 8) {
            items.removeAt(0)
            notifyDataSetChanged()
        } else {
            notifyItemInserted(items.size - 1)
        }
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }
}