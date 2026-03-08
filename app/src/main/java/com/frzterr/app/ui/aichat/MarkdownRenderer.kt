package com.frzterr.app.ui.aichat

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Lightweight Markdown → SpannableString renderer.
 * Supports: **bold**, *italic*, `inline code`, ```code block```, # headers.
 * No external dependencies needed.
 */
object MarkdownRenderer {

    fun render(raw: String): CharSequence {
        val ssb = SpannableStringBuilder()

        // Split per baris untuk handle code block multi-line
        val lines = raw.lines()
        var i = 0
        var isFirst = true

        while (i < lines.size) {
            val line = lines[i]

            // Newline separator antar baris (kecuali baris pertama)
            if (!isFirst) ssb.append("\n")
            isFirst = false

            // ── Fenced code block: ```
            if (line.trimStart().startsWith("```")) {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                // Append code block dengan styling
                val codeText = codeLines.joinToString("\n")
                if (codeText.isNotEmpty()) {
                    val start = ssb.length
                    ssb.append(codeText)
                    val end = ssb.length
                    ssb.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(BackgroundColorSpan(Color.parseColor("#1E1E2E")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(ForegroundColorSpan(Color.parseColor("#A8DADC")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                i++ // skip closing ```
                continue
            }

            // ── Heading: #, ##, ###
            val headingMatch = Regex("^(#{1,3})\\s+(.*)").find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text  = headingMatch.groupValues[2]
                val start = ssb.length
                ssb.append(text)
                val end = ssb.length
                val style = if (level == 1) Typeface.BOLD else Typeface.BOLD_ITALIC
                ssb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i++
                continue
            }

            // ── Bullet list: - item atau * item
            val bulletMatch = Regex("^[\\-\\*]\\s+(.*)").find(line)
            if (bulletMatch != null) {
                val textContent = bulletMatch.groupValues[1]
                ssb.append("• ")
                appendInlineMarkdown(ssb, textContent)
                i++
                continue
            }

            // ── Numbered list: 1. item
            val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)").find(line)
            if (numberedMatch != null) {
                ssb.append("${numberedMatch.groupValues[1]}. ")
                appendInlineMarkdown(ssb, numberedMatch.groupValues[2])
                i++
                continue
            }

            // ── Normal line: proses inline markdown
            appendInlineMarkdown(ssb, line)
            i++
        }

        return ssb
    }

    /**
     * Proses inline markdown dalam satu baris:
     * **bold**, *italic*, `code`
     */
    private fun appendInlineMarkdown(ssb: SpannableStringBuilder, text: String) {
        // Token regex: urutan penting — bold dulu sebelum italic
        val tokenRegex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")
        var lastEnd = 0

        for (match in tokenRegex.findAll(text)) {
            // Teks biasa sebelum match
            if (match.range.first > lastEnd) {
                ssb.append(text.substring(lastEnd, match.range.first))
            }

            val start = ssb.length
            when {
                // **bold**
                match.groupValues[1].isNotEmpty() -> {
                    ssb.append(match.groupValues[1])
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // *italic*
                match.groupValues[2].isNotEmpty() -> {
                    ssb.append(match.groupValues[2])
                    ssb.setSpan(StyleSpan(Typeface.ITALIC), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // `inline code`
                match.groupValues[3].isNotEmpty() -> {
                    ssb.append(match.groupValues[3])
                    ssb.setSpan(TypefaceSpan("monospace"), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(BackgroundColorSpan(Color.parseColor("#2A2A3E")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(ForegroundColorSpan(Color.parseColor("#A8DADC")), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            lastEnd = match.range.last + 1
        }

        // Sisa teks setelah match terakhir
        if (lastEnd < text.length) {
            ssb.append(text.substring(lastEnd))
        }
    }
}
