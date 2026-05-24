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
    val ranges = mutableListOf<TextRange>()
    collectCommentRanges(text, ranges)
    collectTagRanges(text, ranges)
    return ranges
      .filter { document.getLineNumber(it.startOffset) < document.getLineNumber(it.endOffset) }
      .distinctBy { it.startOffset to it.endOffset }
      .map { FoldingDescriptor(node, it) }
      .toTypedArray()
  }

  override fun getPlaceholderText(node: ASTNode): String = "..."

  override fun isCollapsedByDefault(node: ASTNode): Boolean = false

  private fun collectCommentRanges(text: String, ranges: MutableList<TextRange>) {
    var index = 0
    while (index < text.length) {
      val start = text.indexOf("<!--", index)
      if (start < 0) return
      val end = text.indexOf("-->", start + 4)
      if (end < 0) return
      ranges += TextRange(start, end + 3)
      index = end + 3
    }
  }

  private fun collectTagRanges(text: String, ranges: MutableList<TextRange>) {
    val stack = ArrayDeque<TagOpen>()
    val pattern = Regex("""<\s*(/)?\s*([A-Za-z_$][\w$.-]*)([^>]*)>""")
    for (match in pattern.findAll(text)) {
      val full = match.value
      if (full.startsWith("<!--") || full.startsWith("<!")) continue
      val closing = match.groupValues[1].isNotEmpty()
      val name = match.groupValues[2]
      val selfClosing = full.endsWith("/>") || name == "StripePattern"

      if (!closing && !selfClosing) {
        stack.addLast(TagOpen(name, match.range.first))
        continue
      }

      if (closing) {
        val open = popMatching(stack, name) ?: continue
        val end = match.range.last + 1
        if (end - open.offset > name.length + 5) ranges += TextRange(open.offset, end)
      }
    }
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
}
