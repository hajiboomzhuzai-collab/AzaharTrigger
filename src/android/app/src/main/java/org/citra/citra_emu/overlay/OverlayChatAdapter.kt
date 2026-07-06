package org.citra.citra_emu.overlay

import android.graphics.Color
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OverlayChatAdapter : RecyclerView.Adapter<OverlayChatAdapter.ViewHolder>() {

    private val messages = mutableListOf<String>()

    class ViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val tv = TextView(parent.context)
        tv.setTextColor(Color.WHITE)
        tv.textSize = 13f
        return ViewHolder(tv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.text.text = messages[position]
    }

    override fun getItemCount() = messages.size

    fun add(text: String) {
        messages.add(text)

        if (messages.size > 8) {
            messages.removeAt(0)
            notifyDataSetChanged()
        } else {
            notifyItemInserted(messages.lastIndex)
        }
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }
}