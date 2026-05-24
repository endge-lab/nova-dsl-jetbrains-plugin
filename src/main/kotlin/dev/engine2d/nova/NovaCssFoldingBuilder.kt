package dev.engine2d.nova

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange

class NovaCssFoldingBuilder : FoldingBuilder, DumbAware {
  override fun buildFoldRegions(node: ASTNode, document: Document): Array<FoldingDescriptor> {
    val text = document.text
    val regions = mutableListOf<CssFoldRegion>()
    collectCommentRegions(text, regions)
    collectBlockRegions(text, regions)
    return regions
      .filter { document.getLineNumber(it.range.startOffset) < document.getLineNumber(it.range.endOffset) }
      .distinctBy { it.range.startOffset to it.range.endOffset }
      .map { FoldingDescriptor(node, it.range, null, it.placeholder) }
      .toTypedArray()
  }

  override fun getPlaceholderText(node: ASTNode): String = "..."

  override fun isCollapsedByDefault(node: ASTNode): Boolean = false

  private fun collectCommentRegions(text: String, regions: MutableList<CssFoldRegion>) {
    var index = 0
    while (index < text.length) {
      val start = text.indexOf("/*", index)
      if (start < 0) return
      val end = text.indexOf("*/", start + 2)
      if (end < 0) return
      regions += CssFoldRegion(TextRange(start, end + 2), "/* ... */")
      index = end + 2
    }
  }

  private fun collectBlockRegions(text: String, regions: MutableList<CssFoldRegion>) {
    val stack = ArrayDeque<CssBlockOpen>()
    var index = 0
    var quote = 0.toChar()
    var escaped = false
    var inComment = false

    while (index < text.length) {
      val current = text[index]
      val next = text.getOrNull(index + 1)

      if (inComment) {
        if (current == '*' && next == '/') {
          inComment = false
          index += 2
          continue
        }
        index += 1
        continue
      }

      if (quote != 0.toChar()) {
        if (escaped) {
          escaped = false
        } else if (current == '\\') {
          escaped = true
        } else if (current == quote) {
          quote = 0.toChar()
        }
        index += 1
        continue
      }

      if (current == '/' && next == '*') {
        inComment = true
        index += 2
        continue
      }

      if (current == '"' || current == '\'' || current == '`') {
        quote = current
        index += 1
        continue
      }

      when (current) {
        '{' -> stack.addLast(CssBlockOpen(selectorStart = findSelectorStart(text, index), braceOffset = index))
        '}' -> {
          val open = if (stack.isEmpty()) null else stack.removeLast()
          if (open != null && index > open.braceOffset) {
            regions += CssFoldRegion(
              range = TextRange(open.selectorStart, index + 1),
              placeholder = buildBlockPlaceholder(text, open.selectorStart, open.braceOffset),
            )
          }
        }
      }

      index += 1
    }
  }

  private fun findSelectorStart(text: String, braceOffset: Int): Int {
    var index = braceOffset - 1
    var quote = 0.toChar()
    var escaped = false

    while (index >= 0) {
      val current = text[index]

      if (quote != 0.toChar()) {
        if (escaped) {
          escaped = false
        } else if (current == '\\') {
          escaped = true
        } else if (current == quote) {
          quote = 0.toChar()
        }
        index -= 1
        continue
      }

      if (current == '"' || current == '\'' || current == '`') {
        quote = current
        index -= 1
        continue
      }

      if (current == '}' || current == ';') return skipForwardWhitespace(text, index + 1, braceOffset)

      index -= 1
    }

    return skipForwardWhitespace(text, 0, braceOffset)
  }

  private fun skipForwardWhitespace(text: String, start: Int, end: Int): Int {
    var index = start.coerceAtLeast(0)
    while (index < end && text[index].isWhitespace()) index += 1
    return index
  }

  private fun buildBlockPlaceholder(text: String, selectorStart: Int, braceOffset: Int): String {
    val rawHeader = text.substring(selectorStart, braceOffset + 1)
    val firstLine = rawHeader
      .lineSequence()
      .map { it.trim() }
      .firstOrNull { it.isNotEmpty() }
      .orEmpty()

    val normalized = if (rawHeader.contains('\n') || rawHeader.contains('\r')) {
      if (firstLine.endsWith("{")) firstLine else "$firstLine ..."
    } else {
      rawHeader.replace(Regex("\\s+"), " ").trim()
    }

    return normalized.truncatePlaceholder()
  }

  private fun String.truncatePlaceholder(maxLength: Int = 96): String {
    if (length <= maxLength) return this
    return take(maxLength - 4).trimEnd() + " ..."
  }

  private data class CssBlockOpen(
    val selectorStart: Int,
    val braceOffset: Int,
  )

  private data class CssFoldRegion(
    val range: TextRange,
    val placeholder: String,
  )
}
