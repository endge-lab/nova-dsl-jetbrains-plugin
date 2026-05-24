package dev.engine2d.nova

import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class NovaFormattingService : AsyncDocumentFormattingService() {
  override fun getFeatures(): MutableSet<FormattingService.Feature> {
    return mutableSetOf(FormattingService.Feature.FORMAT_FRAGMENTS)
  }

  override fun canFormat(file: PsiFile): Boolean = file.fileType == NovaFileType.INSTANCE

  override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask {
    return object : FormattingTask {
      @Volatile
      private var cancelled = false

      override fun run() {
        if (cancelled) return
        request.onTextReady(formatRanges(request.getDocumentText(), request.getFormattingRanges()))
      }

      override fun cancel(): Boolean {
        cancelled = true
        return true
      }
    }
  }

  override fun getNotificationGroupId(): String = "Nova DSL"

  override fun getName(): String = "Nova DSL Formatter"

  private fun formatRanges(source: String, ranges: List<TextRange>): String {
    if (ranges.isEmpty() || ranges.any { it.startOffset == 0 && it.endOffset == source.length }) {
      return NovaDocumentFormatter.format(source)
    }

    val result = StringBuilder(source.length)
    var cursor = 0
    for (range in ranges.sortedBy { it.startOffset }) {
      val start = range.startOffset.coerceIn(0, source.length)
      val end = range.endOffset.coerceIn(start, source.length)
      if (start < cursor) continue

      result.append(source, cursor, start)
      result.append(NovaDocumentFormatter.format(source.substring(start, end)))
      cursor = end
    }
    result.append(source, cursor, source.length)
    return result.toString()
  }
}
