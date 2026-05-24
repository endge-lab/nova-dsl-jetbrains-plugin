package dev.engine2d.nova

import com.intellij.codeInsight.generation.CommenterDataHolder
import com.intellij.codeInsight.generation.SelfManagingCommenter
import com.intellij.lang.Commenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class NovaCommenter : Commenter, SelfManagingCommenter<NovaCommenter.CommentState> {
  override fun getLineCommentPrefix(): String = "//"

  override fun getBlockCommentPrefix(): String = "<!--"

  override fun getBlockCommentSuffix(): String = "-->"

  override fun getCommentedBlockCommentPrefix(): String? = null

  override fun getCommentedBlockCommentSuffix(): String? = null

  override fun createLineCommentingState(
    startLine: Int,
    endLine: Int,
    document: Document,
    file: PsiFile,
  ): CommentState = CommentState

  override fun createBlockCommentingState(
    selectionStart: Int,
    selectionEnd: Int,
    document: Document,
    file: PsiFile,
  ): CommentState = CommentState

  override fun commentLine(line: Int, offset: Int, document: Document, data: CommentState) {
    val section = sectionAt(document, offsetAtLine(document, line))
    if (section == NovaSection.Script) {
      val contentStart = contentStartOffset(document, line)
      document.insertString(contentStart, "//")
      return
    }

    val (prefix, suffix) = when (section) {
      NovaSection.Style -> "/* " to " */"
      else -> "<!-- " to " -->"
    }
    wrapLine(document, line, prefix, suffix)
  }

  override fun uncommentLine(line: Int, offset: Int, document: Document, data: CommentState) {
    val section = sectionAt(document, offsetAtLine(document, line))
    if (section == NovaSection.Script) {
      removeLinePrefix(document, line, "//")
      return
    }

    val (prefix, suffix) = when (section) {
      NovaSection.Style -> "/*" to "*/"
      else -> "<!--" to "-->"
    }
    unwrapLine(document, line, prefix, suffix)
  }

  override fun isLineCommented(line: Int, offset: Int, document: Document, data: CommentState): Boolean {
    val contentStart = contentStartOffset(document, line)
    if (contentStart >= document.textLength) return false
    val section = sectionAt(document, contentStart)
    return when (section) {
      NovaSection.Script -> document.textMatches(contentStart, "//")
      NovaSection.Style -> lineHasWrapper(document, line, "/*", "*/")
      NovaSection.Template -> lineHasWrapper(document, line, "<!--", "-->")
    }
  }

  override fun getCommentPrefix(line: Int, document: Document, data: CommentState): String {
    return when (sectionAt(document, offsetAtLine(document, line))) {
      NovaSection.Script -> "//"
      NovaSection.Style -> "/*"
      NovaSection.Template -> "<!--"
    }
  }

  override fun getBlockCommentRange(
    selectionStart: Int,
    selectionEnd: Int,
    document: Document,
    data: CommentState,
  ): TextRange = TextRange(selectionStart, selectionEnd)

  override fun getBlockCommentPrefix(selectionStart: Int, document: Document, data: CommentState): String {
    return when (sectionAt(document, selectionStart)) {
      NovaSection.Script,
      NovaSection.Style,
      -> "/*"
      NovaSection.Template -> "<!--"
    }
  }

  override fun getBlockCommentSuffix(selectionEnd: Int, document: Document, data: CommentState): String {
    val offset = if (selectionEnd > 0) selectionEnd - 1 else selectionEnd
    return when (sectionAt(document, offset)) {
      NovaSection.Script,
      NovaSection.Style,
      -> "*/"
      NovaSection.Template -> "-->"
    }
  }

  override fun uncommentBlockComment(startOffset: Int, endOffset: Int, document: Document, data: CommentState) {
    val section = sectionAt(document, startOffset)
    val (prefix, suffix) = when (section) {
      NovaSection.Script,
      NovaSection.Style,
      -> "/*" to "*/"
      NovaSection.Template -> "<!--" to "-->"
    }

    val start = startOffset.coerceIn(0, document.textLength)
    val end = endOffset.coerceIn(start, document.textLength)
    if (!document.textMatches(start, prefix)) return

    val suffixStart = (end - suffix.length).coerceAtLeast(start + prefix.length)
    if (!document.textMatches(suffixStart, suffix)) return

    document.deleteString(suffixStart, suffixStart + suffix.length)
    document.deleteString(start, start + prefix.length)
  }

  override fun insertBlockComment(
    startOffset: Int,
    endOffset: Int,
    document: Document,
    data: CommentState,
  ): TextRange {
    val prefix = getBlockCommentPrefix(startOffset, document, data)
    val suffix = getBlockCommentSuffix(endOffset, document, data)
    val start = startOffset.coerceIn(0, document.textLength)
    val end = endOffset.coerceIn(start, document.textLength)

    document.insertString(end, suffix)
    document.insertString(start, prefix)

    return TextRange(start, end + prefix.length + suffix.length)
  }

  object CommentState : CommenterDataHolder()

  private enum class NovaSection {
    Template,
    Script,
    Style,
  }

  private fun sectionAt(document: Document, offset: Int): NovaSection {
    val text = document.charsSequence.toString()
    if (text.isEmpty()) return NovaSection.Template
    val safeOffset = offset.coerceIn(0, text.length - 1)

    val scriptStart = text.lastIndexOf("<script", safeOffset, ignoreCase = true)
    val scriptEnd = text.lastIndexOf("</script>", safeOffset, ignoreCase = true)
    if (scriptStart > scriptEnd && isPastOpeningTag(text, scriptStart, safeOffset)) return NovaSection.Script

    val styleStart = text.lastIndexOf("<style", safeOffset, ignoreCase = true)
    val styleEnd = text.lastIndexOf("</style>", safeOffset, ignoreCase = true)
    if (styleStart > styleEnd && isPastOpeningTag(text, styleStart, safeOffset)) return NovaSection.Style

    return NovaSection.Template
  }

  private fun isPastOpeningTag(text: String, tagStart: Int, offset: Int): Boolean {
    if (tagStart < 0) return false
    val tagEnd = text.indexOf('>', tagStart)
    return tagEnd >= 0 && offset > tagEnd
  }

  private fun offsetAtLine(document: Document, line: Int): Int {
    if (document.lineCount == 0) return 0
    return document.getLineStartOffset(line.coerceIn(0, document.lineCount - 1))
  }

  private fun contentStartOffset(document: Document, line: Int): Int {
    val lineStart = offsetAtLine(document, line)
    val lineEnd = document.getLineEndOffset(line.coerceIn(0, document.lineCount - 1))
    var offset = lineStart
    val chars = document.charsSequence
    while (offset < lineEnd && (chars[offset] == ' ' || chars[offset] == '\t')) offset += 1
    return offset
  }

  private fun wrapLine(document: Document, line: Int, prefix: String, suffix: String) {
    val lineIndex = line.coerceIn(0, document.lineCount - 1)
    val contentStart = contentStartOffset(document, lineIndex)
    val lineEnd = document.getLineEndOffset(lineIndex)
    document.insertString(lineEnd, suffix)
    document.insertString(contentStart, prefix)
  }

  private fun removeLinePrefix(document: Document, line: Int, prefix: String) {
    val contentStart = contentStartOffset(document, line)
    if (document.textMatches(contentStart, prefix)) {
      document.deleteString(contentStart, contentStart + prefix.length)
    }
  }

  private fun unwrapLine(document: Document, line: Int, prefix: String, suffix: String) {
    val lineIndex = line.coerceIn(0, document.lineCount - 1)
    val contentStart = contentStartOffset(document, lineIndex)
    if (!document.textMatches(contentStart, prefix)) return

    var prefixEnd = contentStart + prefix.length
    if (prefixEnd < document.textLength && document.charsSequence[prefixEnd] == ' ') prefixEnd += 1

    val lineEnd = document.getLineEndOffset(lineIndex)
    var contentEnd = lineEnd
    val chars = document.charsSequence
    while (contentEnd > prefixEnd && chars[contentEnd - 1].isWhitespace()) contentEnd -= 1

    val suffixStart = contentEnd - suffix.length
    if (suffixStart < prefixEnd || !document.textMatches(suffixStart, suffix)) return

    var suffixDeleteStart = suffixStart
    if (suffixDeleteStart > prefixEnd && chars[suffixDeleteStart - 1] == ' ') suffixDeleteStart -= 1

    document.deleteString(suffixDeleteStart, contentEnd)
    document.deleteString(contentStart, prefixEnd)
  }

  private fun lineHasWrapper(document: Document, line: Int, prefix: String, suffix: String): Boolean {
    val lineIndex = line.coerceIn(0, document.lineCount - 1)
    val contentStart = contentStartOffset(document, lineIndex)
    if (!document.textMatches(contentStart, prefix)) return false

    var contentEnd = document.getLineEndOffset(lineIndex)
    val chars = document.charsSequence
    while (contentEnd > contentStart && chars[contentEnd - 1].isWhitespace()) contentEnd -= 1
    return contentEnd >= contentStart + prefix.length + suffix.length &&
      document.textMatches(contentEnd - suffix.length, suffix)
  }

  private fun Document.textMatches(offset: Int, value: String): Boolean {
    if (offset < 0 || offset + value.length > textLength) return false
    for (index in value.indices) {
      if (charsSequence[offset + index] != value[index]) return false
    }
    return true
  }
}
