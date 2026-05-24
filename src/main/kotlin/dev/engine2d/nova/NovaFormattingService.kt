package dev.engine2d.nova

import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class NovaFormattingService : AsyncDocumentFormattingService() {
  override fun getFeatures(): MutableSet<FormattingService.Feature> {
    return mutableSetOf(
      FormattingService.Feature.AD_HOC_FORMATTING,
      FormattingService.Feature.FORMAT_FRAGMENTS,
    )
  }

  override fun canFormat(file: PsiFile): Boolean = file.fileType in SUPPORTED_FILE_TYPES

  override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask {
    return object : FormattingTask {
      @Volatile
      private var cancelled = false

      override fun run() {
        if (cancelled) return
        val formatter = formatterFor(request.getContext().containingFile)
        request.onTextReady(formatRanges(request.getDocumentText(), request.getFormattingRanges(), formatter))
      }

      override fun cancel(): Boolean {
        cancelled = true
        return true
      }
    }
  }

  override fun getNotificationGroupId(): String = "Nova DSL"

  override fun getName(): String = "Nova Formatter"

  private fun formatRanges(source: String, ranges: List<TextRange>, formatter: (String) -> String): String {
    if (ranges.isEmpty() || ranges.any { it.startOffset == 0 && it.endOffset == source.length }) {
      return formatter(source)
    }

    val result = StringBuilder(source.length)
    var cursor = 0
    for (range in ranges.sortedBy { it.startOffset }) {
      val start = range.startOffset.coerceIn(0, source.length)
      val end = range.endOffset.coerceIn(start, source.length)
      if (start < cursor) continue

      result.append(source, cursor, start)
      result.append(formatter(source.substring(start, end)))
      cursor = end
    }
    result.append(source, cursor, source.length)
    return result.toString()
  }

  private fun formatterFor(file: PsiFile): (String) -> String {
    return when (file.fileType) {
      NovaCssFileType.INSTANCE -> NovaCssDocumentFormatter::format
      else -> NovaDocumentFormatter::format
    }
  }

  private companion object {
    private val SUPPORTED_FILE_TYPES = setOf(NovaFileType.INSTANCE, NovaCssFileType.INSTANCE)
  }
}
