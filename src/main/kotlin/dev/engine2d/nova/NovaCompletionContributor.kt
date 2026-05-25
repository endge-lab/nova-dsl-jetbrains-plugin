package dev.engine2d.nova

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
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
    val file = parameters.originalFile.virtualFile
    val text = parameters.editor.document.text
    val offset = parameters.offset
    val completionContext = NovaCompletionContext.analyze(text, offset)
    val components = NovaComponentRegistry.components(project)

    when (completionContext.kind) {
      CompletionKind.TAG -> {
        val imported = importedComponents(text)
        for (item in components) {
          result.addElement(componentLookup(item, imported.contains(item.name)))
        }
        for (local in NovaLocalComponentIndex.components(project, file)) {
          result.addElement(localComponentLookup(local, file, imported.contains(local.name)))
        }
        result.addElement(LookupElementBuilder.create("StripePattern").withTypeText("Nova assets", true))
      }

      CompletionKind.ATTRIBUTE -> {
        val component = completionContext.componentName ?: return
        val doc = components.firstOrNull { it.name == component }
        if (doc != null) {
          for (prop in doc.props.sortedWith(compareByDescending<NovaComponentPropDoc> { it.required }.thenBy { it.name })) {
            result.addElement(propLookup(prop))
          }
        }
        for (directive in DIRECTIVE_PROPS) {
          result.addElement(propLookup(directive))
        }
        if (component == "Icon" || component.endsWith(".Icon")) {
          result.addElement(propLookup(NovaComponentPropDoc("src", "string", false, "Путь к asset.")))
          result.addElement(propLookup(NovaComponentPropDoc("asset-color", "string", false, "Цвет SVG asset.")))
        }
        if (component == "Rect") {
          result.addElement(propLookup(NovaComponentPropDoc("fill-pattern", "string", false, "ID StripePattern.")))
        }
      }

      CompletionKind.EXPRESSION -> {
        for (symbol in scriptSymbols(text)) {
          result.addElement(LookupElementBuilder.create(symbol).withTypeText("script setup", true))
        }
        for (symbol in EXPRESSION_GLOBALS) {
          result.addElement(LookupElementBuilder.create(symbol).withTypeText("Nova expression", true))
        }
      }

      CompletionKind.NONE -> Unit
    }
  }

  private fun componentLookup(component: NovaComponentDoc, alreadyImported: Boolean): LookupElementBuilder {
    val importRequest = component.importSource
      ?.takeIf { it.isNotBlank() && !alreadyImported }
      ?.let { NovaImportRequest.Manifest(component.name, it, component.importName) }
    return LookupElementBuilder
      .create(component.name)
      .withTypeText(component.groupTitle, true)
      .withTailText(" ${component.description}", true)
      .withBoldness(alreadyImported)
      .withInsertHandler(ComponentInsertHandler(component.snippet, importRequest))
  }

  private fun localComponentLookup(
    component: NovaLocalComponent,
    currentFile: VirtualFile?,
    alreadyImported: Boolean,
  ): LookupElementBuilder {
    return LookupElementBuilder
      .create(component.name)
      .withTypeText("local .nova", true)
      .withTailText(" ${component.file.path}", true)
      .withBoldness(alreadyImported)
      .withInsertHandler(ComponentInsertHandler("<${component.name}>\n  \$END\$\n</${component.name}>", NovaImportRequest.Local(component, currentFile)))
  }

  private fun propLookup(prop: NovaComponentPropDoc): LookupElementBuilder {
    return LookupElementBuilder
      .create(prop.name)
      .withPresentableText(prop.name)
      .withTypeText(prop.type, true)
      .withTailText(if (prop.required) " required" else " ${prop.description}", true)
      .withInsertHandler(PropInsertHandler(prop))
  }
}

private class ComponentInsertHandler(
  private val snippet: String,
  private val importRequest: NovaImportRequest?,
) : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val document = context.document
    val project = context.project
    WriteCommandAction.runWriteCommandAction(project, "Insert Nova component", null, Runnable {
      importRequest?.let { insertImport(document, it) }
      replaceWithSnippet(context, snippet)
    }, context.file)
  }
}

private class PropInsertHandler(private val prop: NovaComponentPropDoc) : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val attribute = if (prop.needsExpression()) {
      ":${prop.name}=\"\$END\$\""
    } else {
      "${prop.name}=\"\$END\$\""
    }
    replaceWithSnippet(context, attribute)
  }
}

private sealed interface NovaImportRequest {
  data class Local(val component: NovaLocalComponent, val currentFile: VirtualFile?) : NovaImportRequest
  data class Manifest(val componentName: String, val importSource: String, val importName: String?) : NovaImportRequest
}

private object NovaCompletionContext {
  fun analyze(text: String, offset: Int): CompletionContext {
    val cursor = offset.coerceIn(0, text.length)
    val tagStart = text.lastIndexOf('<', cursor - 1)
    val tagEnd = text.lastIndexOf('>', cursor - 1)
    if (tagStart < 0 || tagEnd > tagStart) return CompletionContext(CompletionKind.NONE)

    val tagSource = text.substring(tagStart + 1, cursor)
    if (tagSource.startsWith("/") || tagSource.contains("<")) return CompletionContext(CompletionKind.NONE)
    val tagName = TAG_NAME_PATTERN.find(tagSource)?.groupValues?.get(1)

    if (tagName == null || tagSource.trim().matches(Regex("""[A-Za-z_$][\w$.-]*"""))) {
      return CompletionContext(CompletionKind.TAG)
    }

    if (isInAttributeExpression(text, cursor, tagStart)) {
      return CompletionContext(CompletionKind.EXPRESSION, tagName)
    }

    return CompletionContext(CompletionKind.ATTRIBUTE, tagName)
  }

  private fun isInAttributeExpression(text: String, offset: Int, tagStart: Int): Boolean {
    val quoteStart = findOpenQuote(text, offset, tagStart) ?: return false
    val prefix = text.substring(tagStart + 1, quoteStart)
    val attribute = ATTRIBUTE_BEFORE_QUOTE_PATTERN.find(prefix)?.groupValues?.get(1) ?: return false
    return attribute.startsWith(":") ||
      attribute.startsWith("@") ||
      attribute.startsWith("#") ||
      attribute.startsWith("v-") ||
      attribute in EXPRESSION_ATTRIBUTES
  }

  private fun findOpenQuote(text: String, offset: Int, lowerBound: Int): Int? {
    var index = offset - 1
    while (index > lowerBound) {
      val current = text[index]
      if (current == '"' || current == '\'' || current == '`') {
        val closing = text.indexOf(current, index + 1)
        if (closing == -1 || closing >= offset) return index
      }
      if (current == '<' || current == '>') return null
      index -= 1
    }
    return null
  }
}

private data class CompletionContext(
  val kind: CompletionKind,
  val componentName: String? = null,
)

private enum class CompletionKind {
  TAG,
  ATTRIBUTE,
  EXPRESSION,
  NONE,
}

private fun replaceWithSnippet(context: InsertionContext, rawSnippet: String) {
  val document = context.document
  val project = context.project
  val template = TemplateManager.getInstance(project).createTemplate("", "Nova")
  val placeholder = Regex("""\$([A-Za-z_][A-Za-z0-9_]*)\$""")
  var cursor = 0
  for (match in placeholder.findAll(rawSnippet)) {
    if (match.range.first > cursor) {
      template.addTextSegment(rawSnippet.substring(cursor, match.range.first))
    }
    val name = match.groupValues[1]
    if (name == "END") {
      template.addEndVariable()
    } else {
      template.addVariable(name, "", "", true)
    }
    cursor = match.range.last + 1
  }
  if (cursor < rawSnippet.length) {
    template.addTextSegment(rawSnippet.substring(cursor))
  }

  document.deleteString(context.startOffset, context.tailOffset)
  context.commitDocument()
  context.editor.caretModel.moveToOffset(context.startOffset)
  TemplateManager.getInstance(project).startTemplate(context.editor, template)
}

private fun insertImport(document: Document, request: NovaImportRequest) {
  val importLine = when (request) {
    is NovaImportRequest.Local -> {
      val currentFile = request.currentFile ?: return
      val parent = currentFile.parent ?: return
      val path = VfsUtilCore.getRelativePath(request.component.file, parent, '/') ?: return
      val importPath = if (path.startsWith(".")) path else "./$path"
      "import ${request.component.name} from '$importPath'"
    }
    is NovaImportRequest.Manifest -> {
      val importName = request.importName?.takeIf { it.isNotBlank() }
      when {
        importName == null || importName == "default" -> "import ${request.componentName} from '${request.importSource}'"
        importName == request.componentName -> "import { ${request.componentName} } from '${request.importSource}'"
        else -> "import { $importName as ${request.componentName} } from '${request.importSource}'"
      }
    }
  }
  val text = document.text
  if (text.contains(importLine)) return

  val scriptRange = SCRIPT_SETUP_OPEN_PATTERN.find(text)
  if (scriptRange == null) {
    document.insertString(0, "<script setup lang=\"ts\">\n$importLine\n</script>\n\n")
    return
  }

  val contentStart = scriptRange.range.last + 1
  val close = SCRIPT_CLOSE_PATTERN.find(text, contentStart)
  val contentEnd = close?.range?.first ?: text.length
  val script = text.substring(contentStart, contentEnd)
  val lastImport = IMPORT_LINE_PATTERN.findAll(script).lastOrNull()
  val insertOffset = if (lastImport != null) {
    contentStart + lastImport.range.last + 1
  } else {
    contentStart
  }
  val prefix = if (insertOffset > 0 && document.charsSequence.getOrNull(insertOffset - 1) == '\n') "" else "\n"
  document.insertString(insertOffset, "$prefix$importLine\n")
}

private fun importedComponents(text: String): Set<String> {
  val result = linkedSetOf<String>()
  DEFAULT_IMPORT_PATTERN.findAll(text).forEach { result.add(it.groupValues[1]) }
  NAMED_IMPORT_PATTERN.findAll(text).forEach { match ->
    match.groupValues[1].split(',').map { it.trim() }.forEach { specifier ->
      val imported = specifier.removePrefix("type ").trim()
      val local = imported.substringAfter(" as ", imported).trim()
      if (local.isNotBlank()) result.add(local)
    }
  }
  return result
}

private fun scriptSymbols(text: String): Set<String> {
  val range = scriptSetupRange(text) ?: return emptySet()
  val script = range.substring(text)
  val result = linkedSetOf<String>()
  LOCAL_SYMBOL_PATTERN.findAll(script).forEach { result.add(it.groupValues[1]) }
  DEFAULT_IMPORT_PATTERN.findAll(script).forEach { result.add(it.groupValues[1]) }
  NAMED_IMPORT_PATTERN.findAll(script).forEach { match ->
    match.groupValues[1].split(',').map { it.trim() }.forEach { specifier ->
      val imported = specifier.removePrefix("type ").trim()
      val local = imported.substringAfter(" as ", imported).trim()
      if (local.isNotBlank()) result.add(local)
    }
  }
  return result
}

private fun scriptSetupRange(text: String): TextRange? {
  val open = SCRIPT_SETUP_OPEN_PATTERN.find(text) ?: return null
  val close = SCRIPT_CLOSE_PATTERN.find(text, open.range.last + 1) ?: return null
  return TextRange(open.range.last + 1, close.range.first)
}

private fun NovaComponentPropDoc.needsExpression(): Boolean {
  if (name in STRING_PROPS) return false
  if (name in EXPRESSION_ATTRIBUTES) return true
  val normalizedType = type.lowercase()
  return normalizedType.contains("number") ||
    normalizedType.contains("boolean") ||
    normalizedType.contains("object") ||
    normalizedType.contains("array") ||
    normalizedType.contains("function") ||
    normalizedType.contains("=>") ||
    normalizedType.contains("|")
}

private val TAG_NAME_PATTERN = Regex("""^\s*([A-Za-z_$][\w$.-]*)""")
private val ATTRIBUTE_BEFORE_QUOTE_PATTERN = Regex("""([:@#]?[A-Za-z_$][\w$.-]*)\s*=\s*$""")
private val SCRIPT_SETUP_OPEN_PATTERN = Regex("""<script\b[^>]*\bsetup\b[^>]*>""", RegexOption.IGNORE_CASE)
private val SCRIPT_CLOSE_PATTERN = Regex("""</script\s*>""", RegexOption.IGNORE_CASE)
private val IMPORT_LINE_PATTERN = Regex("""(?m)^\s*import\b[^\n]*(?:\n|$)""")
private val DEFAULT_IMPORT_PATTERN = Regex("""import\s+([A-Za-z_$][\w$]*)\s+from\s+['"]([^'"]+)['"]""")
private val NAMED_IMPORT_PATTERN = Regex("""import\s+(?:type\s+)?\{([^}]+)}\s+from\s+['"]([^'"]+)['"]""")
private val LOCAL_SYMBOL_PATTERN = Regex("""\b(?:const|let|var|function|class|interface|type|enum)\s+([A-Za-z_$][\w$]*)\b""")

private val STRING_PROPS = setOf(
  "id",
  "ref",
  "class",
  "className",
  "src",
  "source",
  "background",
  "color",
  "text",
  "title",
  "label",
  "name",
  "mount",
  "styleSheet",
  "fill-pattern",
  "asset-color",
)

private val EXPRESSION_ATTRIBUTES = setOf(
  "for",
  "if",
  "else-if",
  "show",
  "key",
  "model",
  "v-for",
  "v-if",
  "v-else-if",
  "v-show",
  "v-model",
)

private val DIRECTIVE_PROPS = listOf(
  NovaComponentPropDoc("for", "expression", false, "Повтор элементов."),
  NovaComponentPropDoc("if", "expression", false, "Условие отрисовки."),
  NovaComponentPropDoc("else-if", "expression", false, "Дополнительная ветка условия."),
  NovaComponentPropDoc("show", "expression", false, "Условие видимости."),
  NovaComponentPropDoc("key", "expression", false, "Ключ reconcile списка."),
  NovaComponentPropDoc("ref", "string", false, "Template ref."),
  NovaComponentPropDoc("class", "string", false, "NovaCSS class."),
)

private val EXPRESSION_GLOBALS = listOf("props", "Nova", "store", "api", "Math", "Date", "Array", "Object")
