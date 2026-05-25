package dev.engine2d.nova

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class NovaCssCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), NovaCssCompletionProvider())
  }
}

private class NovaCssCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet,
  ) {
    val text = parameters.editor.document.text
    val offset = parameters.offset
    val cssContext = NovaCssCompletionContext.analyze(text, offset)

    when (cssContext) {
      CssCompletionKind.AT_RULE -> AT_RULES.forEach {
        result.addElement(LookupElementBuilder.create(it).withTypeText("NovaCSS at-rule", true))
      }

      CssCompletionKind.PROPERTY -> CSS_PROPERTIES.forEach {
        result.addElement(LookupElementBuilder.create(it).withTypeText("property", true).withTailText(": ", true))
      }

      CssCompletionKind.VALUE -> {
        CSS_VALUES.forEach {
          result.addElement(LookupElementBuilder.create(it).withTypeText("value", true))
        }
        collectCustomProperties(parameters.originalFile.virtualFile, text).forEach {
          result.addElement(LookupElementBuilder.create("var($it)").withLookupString(it).withTypeText("custom property", true))
        }
      }

      CssCompletionKind.CUSTOM_PROPERTY -> collectCustomProperties(parameters.originalFile.virtualFile, text).forEach {
        result.addElement(LookupElementBuilder.create(it).withTypeText("custom property", true))
      }

      CssCompletionKind.SELECTOR -> Unit
    }
  }
}

private object NovaCssCompletionContext {
  fun analyze(text: String, offset: Int): CssCompletionKind {
    val cursor = offset.coerceIn(0, text.length)
    val prefix = text.substring(maxOf(0, cursor - 80), cursor)
    val trimmed = prefix.trimStart()

    if (trimmed.startsWith("@")) return CssCompletionKind.AT_RULE
    if (prefix.endsWith("var(") || prefix.endsWith("var( ")) return CssCompletionKind.CUSTOM_PROPERTY

    val lastBrace = text.lastIndexOf('{', cursor - 1)
    val lastClose = text.lastIndexOf('}', cursor - 1)
    if (lastBrace > lastClose) {
      val lastColon = text.lastIndexOf(':', cursor - 1)
      val lastSemi = text.lastIndexOf(';', cursor - 1)
      return if (lastColon > lastSemi && lastColon > lastBrace) CssCompletionKind.VALUE else CssCompletionKind.PROPERTY
    }

    return CssCompletionKind.SELECTOR
  }
}

private enum class CssCompletionKind {
  AT_RULE,
  PROPERTY,
  VALUE,
  CUSTOM_PROPERTY,
  SELECTOR,
}

private fun collectCustomProperties(currentFile: VirtualFile?, currentText: String): Set<String> {
  val result = linkedSetOf<String>()
  CUSTOM_PROPERTY_DECLARATION.findAll(currentText).forEach { result.add(it.groupValues[1]) }

  val root = findWorkspaceRoot(currentFile)
  if (root != null) {
    collectNovaCssFiles(root, result, 0)
  }

  return result
}

private fun collectNovaCssFiles(file: VirtualFile, result: MutableSet<String>, depth: Int) {
  if (result.size >= MAX_CUSTOM_PROPERTIES || depth > MAX_CSS_DEPTH) return
  if (file.isDirectory) {
    if (file.name in SKIPPED_CSS_DIRS) return
    for (child in file.children.take(MAX_CSS_CHILDREN)) {
      collectNovaCssFiles(child, result, depth + 1)
      if (result.size >= MAX_CUSTOM_PROPERTIES) return
    }
    return
  }

  if (file.extension != "novacss") return
  val text = runCatching { String(file.contentsToByteArray()) }.getOrNull() ?: return
  CUSTOM_PROPERTY_DECLARATION.findAll(text).forEach { result.add(it.groupValues[1]) }
}

private fun findWorkspaceRoot(file: VirtualFile?): VirtualFile? {
  val start = file ?: return null
  val root = generateSequence(if (start.isDirectory) start else start.parent) { it.parent }
    .firstOrNull { it.findChild("package.json") != null }
  if (root != null) return root

  val basePath = LocalFileSystem.getInstance().findFileByPath(start.path)?.parent?.path ?: return null
  return LocalFileSystem.getInstance().findFileByPath(basePath)
}

private val AT_RULES = listOf("@theme", "@media", "@supports")
private val CSS_PROPERTIES = listOf(
  "background",
  "borderColor",
  "borderRadius",
  "borderWidth",
  "color",
  "cursor",
  "display",
  "fontFamily",
  "fontSize",
  "fontWeight",
  "gap",
  "height",
  "hoverBackground",
  "lineHeight",
  "margin",
  "opacity",
  "padding",
  "pressedBackground",
  "width",
  "--nova-bg",
  "--nova-text",
  "--nova-border",
)
private val CSS_VALUES = listOf(
  "transparent",
  "#ffffff",
  "#000000",
  "block",
  "flex",
  "grid",
  "none",
  "pointer",
  "default",
  "var(--)",
  "rgba(0, 0, 0, 0.12)",
)
private val CUSTOM_PROPERTY_DECLARATION = Regex("""(?m)(--[A-Za-z0-9_-]+)\s*:""")
private val SKIPPED_CSS_DIRS = setOf(".git", ".gradle", ".idea", "build", "coverage", "dist", "node_modules", "out")
private const val MAX_CSS_DEPTH = 8
private const val MAX_CSS_CHILDREN = 160
private const val MAX_CUSTOM_PROPERTIES = 800
