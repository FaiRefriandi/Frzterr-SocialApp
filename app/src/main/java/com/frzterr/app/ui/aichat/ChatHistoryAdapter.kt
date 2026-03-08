package com.frzterr.app.ui.aichat

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R

class ChatHistoryAdapter(
    private val sessions: MutableList<ChatSession>,
    private val onSessionClick: (ChatSession) -> Unit,
    private val onDeleteClick: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvSessionTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvTitle.text = session.title

        // Tap → buka sesi
        holder.itemView.setOnClickListener { onSessionClick(session) }

        // Long press → dialog konfirmasi hapus
        holder.itemView.setOnLongClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Hapus riwayat?")
                .setMessage("\"${session.title.take(40)}\" akan dihapus dari riwayat.")
                .setPositiveButton("Hapus") { _, _ -> onDeleteClick(session) }
                .setNegativeButton("Batal", null)
                .show()
            true
        }
    }

    override fun getItemCount() = sessions.size

    fun updateSessions(newSessions: List<ChatSession>) {
        sessions.clear()
        sessions.addAll(newSessions.sortedByDescending { it.timestamp })
        notifyDataSetChanged()
    }
}
