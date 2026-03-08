package com.frzterr.app.ui.aichat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R
import com.frzterr.app.databinding.ItemMessageAiBinding
import com.frzterr.app.databinding.ItemMessageUserBinding

class AiChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<AiChatMessage>()

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1

        /** Resolve icon drawable berdasarkan nama model */
        fun iconForModel(modelName: String): Int = when {
            modelName.contains("Qwen", ignoreCase = true)     -> R.drawable.ic_qwen
            modelName.contains("DeepSeek", ignoreCase = true) -> R.drawable.ic_deepseek
            modelName.contains("GPT", ignoreCase = true)      -> R.drawable.ic_chat_gpt
            else                                               -> R.drawable.ic_ai_star_bold
        }
    }

    /**
     * Replace seluruh list pesan.
     * Gunakan DiffUtil agar streaming update hanya me-rebind item terakhir
     * (bukan menambah bubble baru setiap chunk).
     */
    fun setMessages(newMessages: List<AiChatMessage>) {
        val oldSize = messages.size
        val newSize = newMessages.size

        // Kasus umum saat streaming: list tumbuh 1 item atau item terakhir berubah isinya
        if (newSize == oldSize + 1) {
            // Pesan baru ditambahkan (user mengirim atau placeholder AI muncul)
            messages.add(newMessages.last())
            notifyItemInserted(messages.size - 1)
        } else if (newSize == oldSize && newSize > 0) {
            // Hanya item terakhir yang berubah (streaming chunk update)
            messages[messages.lastIndex] = newMessages.last()
            notifyItemChanged(messages.lastIndex)
        } else {
            // Fallback: full diff (misalnya saat load sesi dari history)
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldSize
                override fun getNewListSize() = newSize
                override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                    messages[oldPos].role == newMessages[newPos].role &&
                    messages[oldPos].content == newMessages[newPos].content
                override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                    messages[oldPos] == newMessages[newPos]
            })
            messages.clear()
            messages.addAll(newMessages)
            diff.dispatchUpdatesTo(this)
        }
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].role == "user") TYPE_USER else TYPE_AI

    override fun getItemCount() = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            AiViewHolder(ItemMessageAiBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is UserViewHolder -> holder.bind(msg.content)
            is AiViewHolder   -> holder.bind(msg)
        }
    }

    inner class UserViewHolder(private val b: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(text: String) { b.tvMessage.text = text }
    }

    inner class AiViewHolder(private val b: ItemMessageAiBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(msg: AiChatMessage) {
            b.tvMessage.text   = MarkdownRenderer.render(msg.content)
            b.tvModelName.text = msg.modelName
            b.ivModelIcon.setImageResource(iconForModel(msg.modelName))
        }
    }
}
