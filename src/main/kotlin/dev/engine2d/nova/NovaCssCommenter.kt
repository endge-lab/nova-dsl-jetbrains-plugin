package dev.engine2d.nova

import com.intellij.codeInsight.generation.CommenterDataHolder
import com.intellij.codeInsight.generation.SelfManagingCommenter
import com.intellij.lang.Commenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class NovaCssCommenter : Commenter, SelfManagingCommenter<NovaCssCommenter.CommentState> {
  override fun getLineCommentPrefix(): String? = null

  override fun getBlockCommentPrefix(): String = "/*"

  override fun getBlockCommentSuffix(): String = "*/"

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
    val lineIndex = line.coerceIn(0, document.lineCount - 1)
    val contentStart = contentStartOffset(document, lineIndex)
    val lineEnd = document.getLineEndOffset(lineIndex)
    document.insertString(lineEnd, " */")
    document.insertString(contentStart, "/* ")
  }

  override fun uncommentLine(line: Int, offset: Int, document: Document, data: CommentState) {
    unwrapLine(document, line)
  }

  override fun isLineCommented(line: Int, offset: Int, document: Document, data: CommentState): Boolean {
    val lineIndex = line.coerceIn(0, document.lineCount - 1)
    val contentStart = contentStartOffset(document, lineIndex)
    if (!document.textMatches(contentStart, "/*")) return false

    var contentEnd = document.getLineEndOffset(lineIndex)
    val chars = document.charsSequence
    while (contentEnd > contentStart && chars[contentEnd - 1].isWhitespace()) contentEnd -= 1
    return contentEnd >= contentStart + 4 && document.textMatches(contentEnd - 2, "*/")
  }

  override fun getCommentPrefix(line: Int, document: Document, data: CommentState): String = "/*"

  override fun getBlockCommentRange(
    selectionStart: Int,
    selectionEnd: Int,
    document: Document,
    data: CommentState,
  ): TextRange = TextRange(selectionStart, selectionEnd)

  override fun getBlockCommentPrefix(selectionStart: Int, document: Document, data: CommentState): String = "/*"

  override fun getBlockCommentSuffix(selectionEnd: Int, document: Document, data: CommentState): String = "*/"

  override fun uncommentBlockComment(startOffset: Int, endOffset: Int, document: Document, data: CommentState) {
    val start = startOffset.coerceIn(0, document.textLength)
    val end = endOffset.coerceIn(start, document.textLength)
    if (!document.textMatches(start, "/*")) return

    val suffixStart = (end - 2).coerceAtLeast(start + 2)
    if (!document.textMatches(suffixStart, "*/")) return

    document.deleteString(suffixStart, suffixStart + 2)
    document.deleteString(start, start + 2)
  }

  override fun insertBlockComment(
    startOffset: Int,
    endOffset: Int,
    document: Document,
    data: CommentState,
  ): TextRange {
    val start = startOffset.coerceIn(0, document.textLength)
    val end = endOffset.coerceIn(start, document.textLength)
    document.insertString(end, "*/")
    document.insertString(start, "/*")
    return TextRange(start, end + 4)
  }

  object CommentState : CommenterDataHolder()

  private fun contentStartOffset(document: Document, line: Int): Int {
    val lineIndex = line.coerceIn(0, document.lineCount - 1)
    val lineStart = document.getLineStartOffset(lineIndex)
    val lineEnd = document.getLineEndOffset(lineIndex)
    var offset = lineStart
    val chars = document.charsSequence
    while (offset < lineEnd && (chars[offset] == ' ' || chars[offset] == '\t')) offset += 1
    return offset
  }

  private fun unwrapLine(document: Document, line: Int) {
    val lineIndex = line.coerceIn(0, document.lineCount - 1)
    val contentStart = contentStartOffset(document, lineIndex)
    if (!document.textMatches(contentStart, "/*")) return

    var prefixEnd = contentStart + 2
    if (prefixEnd < document.textLength && document.charsSequence[prefixEnd] == ' ') prefixEnd += 1

    val lineEnd = document.getLineEndOffset(lineIndex)
    var contentEnd = lineEnd
    val chars = document.charsSequence
    while (contentEnd > prefixEnd && chars[contentEnd - 1].isWhitespace()) contentEnd -= 1

    val suffixStart = contentEnd - 2
    if (suffixStart < prefixEnd || !document.textMatches(suffixStart, "*/")) return

    var suffixDeleteStart = suffixStart
    if (suffixDeleteStart > prefixEnd && chars[suffixDeleteStart - 1] == ' ') suffixDeleteStart -= 1

    document.deleteString(suffixDeleteStart, contentEnd)
    document.deleteString(contentStart, prefixEnd)
  }

  private fun Document.textMatches(offset: Int, value: String): Boolean {
    if (offset < 0 || offset + value.length > textLength) return false
    for (index in value.indices) {
      if (charsSequence[offset + index] != value[index]) return false
    }
    return true
  }
}
