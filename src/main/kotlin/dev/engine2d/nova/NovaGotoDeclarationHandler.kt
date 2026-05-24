package dev.engine2d.nova

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

class NovaGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor,
  ): Array<PsiElement>? {
    val project = sourceElement?.project ?: editor.project ?: return null
    val sourceFile = sourceElement?.containingFile?.virtualFile
      ?: FileDocumentManager.getInstance().getFile(editor.document)
      ?: return null
    val text = editor.document.text

    val pathTarget = resolvePathUnderCaret(project, sourceFile, text, offset)
    if (pathTarget != null) return arrayOf(pathTarget)

    val tagName = findTagNameUnderCaret(text, offset) ?: findIdentifierUnderCaret(text, offset) ?: return null
    val importedTarget = resolveImportedNovaComponent(project, sourceFile, text, tagName)
    if (importedTarget != null) return arrayOf(importedTarget)

    val builtinTarget = resolveBuiltinComponent(project, sourceFile, tagName)
    if (builtinTarget != null) return arrayOf(builtinTarget)

    val manifestTarget = resolveManifestComponent(project, sourceFile, tagName)
    if (manifestTarget != null) return arrayOf(manifestTarget)

    return null
  }

  private fun resolvePathUnderCaret(
    project: Project,
    sourceFile: VirtualFile,
    text: String,
    offset: Int,
  ): PsiElement? {
    val quotedString = findQuotedStringAt(text, offset) ?: return null
    val attributePrefix = text.substring(maxOf(0, quotedString.start - 32), quotedString.start)
    val isSourceAttribute = SOURCE_ATTRIBUTE_PATTERN.containsMatchIn(attributePrefix)
    val isImportSource = IMPORT_SOURCE_PATTERN.containsMatchIn(attributePrefix)
    if (!isSourceAttribute && !isImportSource) return null

    val path = text.substring(quotedString.start + 1, quotedString.end)
    if (!isNavigablePath(path)) return null

    return resolveRelativePsiFile(project, sourceFile, path)
  }

  private fun resolveImportedNovaComponent(
    project: Project,
    sourceFile: VirtualFile,
    text: String,
    tagName: String,
  ): PsiElement? {
    val importPath = NOVA_DEFAULT_IMPORT_PATTERN.findAll(text)
      .firstOrNull { it.groupValues[1] == tagName }
      ?.groupValues
      ?.get(2)
      ?: return null

    return resolveRelativePsiFile(project, sourceFile, importPath)
  }

  private fun resolveBuiltinComponent(
    project: Project,
    sourceFile: VirtualFile,
    tagName: String,
  ): PsiElement? {
    val targetPath = BUILTIN_COMPONENT_TARGETS[tagName] ?: return null
    val workspaceRoot = findWorkspaceRoot(sourceFile) ?: return null
    val targetFile = workspaceRoot.findFileByRelativePath(targetPath) ?: return null
    return PsiManager.getInstance(project).findFile(targetFile)
  }

  private fun resolveManifestComponent(
    project: Project,
    sourceFile: VirtualFile,
    tagName: String,
  ): PsiElement? {
    val source = NovaComponentRegistry.find(project, tagName)?.source ?: return null
    val workspaceRoot = findWorkspaceRoot(sourceFile) ?: findProjectRoot(project) ?: return null
    val targetFile = workspaceRoot.findFileByRelativePath(source) ?: return null
    return PsiManager.getInstance(project).findFile(targetFile)
  }

  private fun resolveRelativePsiFile(
    project: Project,
    sourceFile: VirtualFile,
    rawPath: String,
  ): PsiElement? {
    val normalizedPath = rawPath.substringBefore('?')
    val parent = if (sourceFile.isDirectory) sourceFile else sourceFile.parent ?: return null
    val targetFile = VfsUtil.findRelativeFile(normalizedPath, parent) ?: return null
    return PsiManager.getInstance(project).findFile(targetFile)
  }

  private fun findWorkspaceRoot(sourceFile: VirtualFile): VirtualFile? {
    return generateSequence(if (sourceFile.isDirectory) sourceFile else sourceFile.parent) { it.parent }
      .firstOrNull { it.findChild("packages") != null && it.findChild("package.json") != null }
  }

  private fun findProjectRoot(project: Project): VirtualFile? {
    val basePath = project.basePath ?: return null
    return LocalFileSystem.getInstance().findFileByPath(basePath)
  }

  private fun findQuotedStringAt(text: String, offset: Int): TextSpan? {
    if (text.isEmpty()) return null

    val cursor = offset.coerceIn(0, text.length)
    var start = cursor - 1
    while (start >= 0 && text[start] != '\n' && text[start] != '<') {
      if (text[start] == '"' || text[start] == '\'') break
      start--
    }

    if (start < 0 || text[start] == '\n' || text[start] == '<') return null

    val quote = text[start]
    var end = start + 1
    while (end < text.length && text[end] != '\n' && text[end] != quote) {
      end++
    }

    if (end >= text.length || text[end] != quote) return null
    if (cursor < start || cursor > end) return null

    return TextSpan(start, end)
  }

  private fun findTagNameUnderCaret(text: String, offset: Int): String? {
    val identifier = findIdentifierUnderCaret(text, offset) ?: return null
    val range = findIdentifierRangeAt(text, offset) ?: return null

    var index = range.start - 1
    while (index >= 0 && text[index].isWhitespace()) index--
    if (index >= 0 && text[index] == '/') index--
    while (index >= 0 && text[index].isWhitespace()) index--

    return if (index >= 0 && text[index] == '<') identifier else null
  }

  private fun findIdentifierUnderCaret(text: String, offset: Int): String? {
    val range = findIdentifierRangeAt(text, offset) ?: return null
    return text.substring(range.start, range.end)
  }

  private fun findIdentifierRangeAt(text: String, offset: Int): TextSpan? {
    if (text.isEmpty()) return null

    var cursor = offset.coerceIn(0, text.length - 1)
    if (!isIdentifierChar(text[cursor]) && cursor > 0 && isIdentifierChar(text[cursor - 1])) {
      cursor--
    }
    if (!isIdentifierChar(text[cursor])) return null

    var start = cursor
    while (start > 0 && isIdentifierChar(text[start - 1])) start--

    var end = cursor + 1
    while (end < text.length && isIdentifierChar(text[end])) end++

    return TextSpan(start, end)
  }

  private fun isIdentifierChar(char: Char): Boolean {
    return char.isLetterOrDigit() || char == '_' || char == '$' || char == '.' || char == '-'
  }

  private fun isNavigablePath(path: String): Boolean {
    val normalizedPath = path.substringBefore('?')
    return normalizedPath.endsWith(".nova")
      || normalizedPath.endsWith(".novacss")
      || normalizedPath.endsWith(".svg")
      || normalizedPath.endsWith(".png")
      || normalizedPath.endsWith(".jpg")
      || normalizedPath.endsWith(".jpeg")
      || normalizedPath.endsWith(".webp")
      || normalizedPath.endsWith(".gif")
      || normalizedPath.endsWith(".avif")
  }

  private data class TextSpan(
    val start: Int,
    val end: Int,
  )

  companion object {
    private val SOURCE_ATTRIBUTE_PATTERN = Regex("""(?:^|\s)(?:src|source|icon|background|fill-pattern|fillPattern)\s*=\s*$""")
    private val IMPORT_SOURCE_PATTERN = Regex("""(?:^|\s)from\s*$""")
    private val NOVA_DEFAULT_IMPORT_PATTERN = Regex(
      """import\s+([A-Za-z_$][\w$]*)\s+from\s+['"]([^'"]+\.nova(?:\?[^'"]*)?)['"]""",
    )

    private val BUILTIN_COMPONENT_TARGETS = mapOf(
      "Root" to "packages/@endge-nova-ui-kit/src/components/Root/Root.ts",
      "Flex" to "packages/@endge-nova-ui-kit/src/components/Flex/Flex.ts",
      "Grid" to "packages/@endge-nova-ui-kit/src/components/Grid/Grid.ts",
      "TextBlock" to "packages/@endge-nova-ui-kit/src/components/TextBlock/TextBlock.ts",
      "Surface" to "packages/@endge-nova-ui-kit/src/components/Surface/Surface.ts",
      "Button" to "packages/@endge-nova-ui-kit/src/components/Button/Button.ts",
      "Tag" to "packages/@endge-nova-ui-kit/src/components/Tag/Tag.ts",
      "Input" to "packages/@endge-nova-ui-kit/src/components/Input/Input.ts",
      "SplitPane" to "packages/@endge-nova-ui-kit/src/components/SplitPane/SplitPane.ts",
      "ScrollArea" to "packages/@endge-nova-ui-kit/src/components/ScrollArea/ScrollArea.ts",
      "Scrollbar" to "packages/@endge-nova-ui-kit/src/components/Scrollbar/Scrollbar.ts",
      "Slider" to "packages/@endge-nova-ui-kit/src/components/Slider/Slider.ts",
      "Checkbox" to "packages/@endge-nova-ui-kit/src/components/Checkbox/Checkbox.ts",
      "Toggle" to "packages/@endge-nova-ui-kit/src/components/Toggle/Toggle.ts",
      "Tooltip" to "packages/@endge-nova-ui-kit/src/components/Tooltip/Tooltip.ts",
      "SegmentedControl" to "packages/@endge-nova-ui-kit/src/components/SegmentedControl/SegmentedControl.ts",
      "Panel" to "packages/@endge-nova-ui-kit/src/components/Panel/Panel.ts",
      "ProgressRing" to "packages/@endge-nova-ui-kit/src/components/ProgressRing/progress-ring.schema.ts",
      "ColResizer" to "packages/@endge-nova-ui-kit/src/components/ColResizer/ColResizer.ts",
      "RowResizer" to "packages/@endge-nova-ui-kit/src/components/RowResizer/RowResizer.ts",
      "LazyResizer" to "packages/@endge-nova-ui-kit/src/components/LazyResizer/LazyResizer.ts",
      "Advanced" to "packages/@endge-nova-ui-kit/src/components/Advanced/advanced.ts",

      "TimelineChart.Root" to "packages/timeline-chart/src/features/timeline/ui/root/TimelineRootNode.ts",
      "TimelineChart.GroupPanel" to "packages/timeline-chart/src/features/timeline/vue/timeline-dsl.ts",
      "TimelineChart.GroupColumn" to "packages/timeline-chart/src/features/timeline/vue/timeline-dsl.ts",
      "TimelineChart.GroupsPanel" to "packages/timeline-chart/src/features/timeline/ui/part/TimelinePartNode.ts",
      "TimelineChart.TimeScale" to "packages/timeline-chart/src/features/timeline/ui/part/TimelinePartNode.ts",
      "TimelineChart.TasksPanel" to "packages/timeline-chart/src/features/timeline/ui/part/TimelinePartNode.ts",
      "TimelineChart.Grid" to "packages/timeline-chart/src/features/timeline/ui/part/TimelinePartNode.ts",
      "TimelineChart.UngroupedPanel" to "packages/timeline-chart/src/features/timeline/ui/part/TimelinePartNode.ts",
      "TimelineTaskProfile" to "packages/timeline-chart/src/features/timeline/vue/timeline-dsl.ts",
    )
  }
}
