package dev.engine2d.nova

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class NovaCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), NovaCompletionProvider())
  }
}

private class NovaCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet,
  ) {
    val project = parameters.editor.project ?: return
    val text = parameters.editor.document.text
    val offset = parameters.offset
    val component = findOpenTagName(text, offset)
    val components = NovaComponentRegistry.components(project)

    if (component == null && isTagCompletionPosition(text, offset)) {
      for (item in components) {
        result.addElement(
          LookupElementBuilder
            .create(item.name)
            .withTypeText(item.groupTitle, true)
            .withTailText(" ${item.description}", true),
        )
      }
      result.addElement(LookupElementBuilder.create("StripePattern").withTypeText("Nova assets", true))
      return
    }

    if (component != null) {
      val doc = components.firstOrNull { it.name == component } ?: return
      for (prop in doc.props.sortedWith(compareByDescending<NovaComponentPropDoc> { it.required }.thenBy { it.name })) {
        result.addElement(
          LookupElementBuilder
            .create(prop.name)
            .withTypeText(prop.type, true)
            .withTailText(if (prop.required) " required" else "", true)
            .withPresentableText(prop.name),
        )
      }
      if (component == "Icon" || component.endsWith(".Icon")) {
        result.addElement(LookupElementBuilder.create("src").withTypeText("asset path", true))
        result.addElement(LookupElementBuilder.create("asset-color").withTypeText("svg color", true))
      }
      if (component == "Rect") {
        result.addElement(LookupElementBuilder.create("fill-pattern").withTypeText("StripePattern id", true))
      }
    }
  }

  private fun isTagCompletionPosition(text: String, offset: Int): Boolean {
    val cursor = offset.coerceIn(0, text.length)
    val before = text.substring(maxOf(0, cursor - 80), cursor)
    return before.lastIndexOf('<') > before.lastIndexOf('>')
  }

  private fun findOpenTagName(text: String, offset: Int): String? {
    val cursor = offset.coerceIn(0, text.length)
    val start = text.lastIndexOf('<', cursor - 1)
    val end = text.lastIndexOf('>', cursor - 1)
    if (start < 0 || end > start) return null
    val source = text.substring(start + 1, cursor)
    if (source.startsWith("/") || source.contains("<")) return null
    val match = source.matchAtStart()
    return match?.takeIf { source.contains(' ') || source.contains('\n') || source.contains('\t') }
  }

  private fun String.matchAtStart(): String? {
    val match = Regex("""^\s*([A-Za-z_$][\w$.-]*)""").find(this) ?: return null
    return match.groupValues[1]
  }
}
