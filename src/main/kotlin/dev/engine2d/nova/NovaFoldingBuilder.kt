package dev.engine2d.nova

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange

class NovaFoldingBuilder : FoldingBuilder, DumbAware {
  override fun buildFoldRegions(node: ASTNode, document: Document): Array<FoldingDescriptor> {
    val text = document.text
    val regions = mutableListOf<NovaFoldRegion>()
    collectCommentRanges(text, regions)
    collectTagRanges(text, regions)
    return regions
      .filter { document.getLineNumber(it.range.startOffset) < document.getLineNumber(it.range.endOffset) }
      .distinctBy { it.range.startOffset to it.range.endOffset }
      .map { FoldingDescriptor(node, it.range, null, it.placeholder) }
      .toTypedArray()
  }

  override fun getPlaceholderText(node: ASTNode): String = "..."

  override fun isCollapsedByDefault(node: ASTNode): Boolean = false

  private fun collectCommentRanges(text: String, regions: MutableList<NovaFoldRegion>) {
    var index = 0
    while (index < text.length) {
      val start = text.indexOf("<!--", index)
      if (start < 0) return
      val end = text.indexOf("-->", start + 4)
      if (end < 0) return
      regions += NovaFoldRegion(TextRange(start, end + 3), "<!-- ... -->")
      index = end + 3
    }
  }

  private fun collectTagRanges(text: String, regions: MutableList<NovaFoldRegion>) {
    val stack = ArrayDeque<TagOpen>()
    var index = 0

    while (index < text.length) {
      val start = text.indexOf('<', index)
      if (start < 0) return

      if (text.startsWith("<!--", start)) {
        index = text.indexOf("-->", start + 4).let { if (it < 0) text.length else it + 3 }
        continue
      }

      if (start + 1 >= text.length || text[start + 1] == '!' || text[start + 1] == '?') {
        index = start + 1
        continue
      }

      val tag = readTag(text, start)
      if (tag == null) {
        index = start + 1
        continue
      }

      val closing = tag.closing
      val name = tag.name
      val selfClosing = tag.selfClosing || name == "StripePattern"

      if (!closing && !selfClosing) {
        stack.addLast(TagOpen(name, start))

        if (name == "script" || name == "style") {
          index = tag.end
          continue
        }
      }

      if (closing) {
        val open = popMatching(stack, name)
        if (open != null) {
          val end = tag.end
          if (end - open.offset > name.length + 5) {
            regions += NovaFoldRegion(
              range = TextRange(open.offset, end),
              placeholder = buildTagPlaceholder(text, open.offset, tag.name),
            )
          }
        }
      }

      index = tag.end
    }
  }

  private fun readTag(text: String, start: Int): TagToken? {
    var index = start + 1
    var closing = false
    if (index < text.length && text[index] == '/') {
      closing = true
      index += 1
    }

    while (index < text.length && text[index].isWhitespace()) index += 1
    val nameStart = index
    while (index < text.length && isTagNamePart(text[index])) index += 1
    if (index == nameStart) return null
    val name = text.substring(nameStart, index)

    var quote = 0.toChar()
    var escaped = false
    while (index < text.length) {
      val current = text[index]
      if (quote != 0.toChar()) {
        if (escaped) {
          escaped = false
        } else if (current == '\\') {
          escaped = true
        } else if (current == quote) {
          quote = 0.toChar()
        }
      } else if (current == '"' || current == '\'' || current == '`') {
        quote = current
      } else if (current == '>') {
        val selfClosing = index > start && text.substring(start, index).trimEnd().endsWith("/")
        return TagToken(name = name, closing = closing, selfClosing = selfClosing, end = index + 1)
      }
      index += 1
    }

    return null
  }

  private fun buildTagPlaceholder(text: String, start: Int, tagName: String): String {
    val token = readTag(text, start) ?: return "<$tagName ...>"
    val rawOpeningTag = text.substring(start, token.end)
    val firstLine = rawOpeningTag
      .lineSequence()
      .firstOrNull()
      ?.trim()
      .orEmpty()

    val normalized = if (rawOpeningTag.contains('\n') || rawOpeningTag.contains('\r')) {
      when {
        firstLine.length > tagName.length + 2 -> "$firstLine ..."
        else -> "<$tagName ...>"
      }
    } else {
      rawOpeningTag.replace(Regex("\\s+"), " ").trim()
    }

    return normalized.truncatePlaceholder()
  }

  private fun String.truncatePlaceholder(maxLength: Int = 96): String {
    if (length <= maxLength) return this
    return take(maxLength - 4).trimEnd() + " ..."
  }

  private fun popMatching(stack: ArrayDeque<TagOpen>, name: String): TagOpen? {
    while (stack.isNotEmpty()) {
      val open = stack.removeLast()
      if (open.name == name) return open
    }
    return null
  }

  private data class TagOpen(
    val name: String,
    val offset: Int,
  )

  private data class TagToken(
    val name: String,
    val closing: Boolean,
    val selfClosing: Boolean,
    val end: Int,
  )

  private data class NovaFoldRegion(
    val range: TextRange,
    val placeholder: String,
  )

  private fun isTagNamePart(value: Char): Boolean {
    return value.isLetterOrDigit() || value == '_' || value == '$' || value == '-' || value == '.'
  }
}
