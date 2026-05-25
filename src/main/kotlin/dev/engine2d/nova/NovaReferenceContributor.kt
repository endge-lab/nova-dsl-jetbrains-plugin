package dev.engine2d.nova

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext

class NovaReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(NovaTokenTypes.IDENTIFIER),
      NovaIdentifierReferenceProvider(),
    )
  }
}

private class NovaIdentifierReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(
    element: PsiElement,
    context: ProcessingContext,
  ): Array<PsiReference> {
    if (element.containingFile !is NovaPsiFile) return PsiReference.EMPTY_ARRAY
    if (element.elementType != NovaTokenTypes.IDENTIFIER) return PsiReference.EMPTY_ARRAY
    if (!isIdentifier(element.text)) return PsiReference.EMPTY_ARRAY

    return arrayOf(NovaIdentifierReference(element))
  }
}

private class NovaIdentifierReference(element: PsiElement) : PsiReferenceBase<PsiElement>(
  element,
  TextRange(0, element.textLength),
  false,
) {
  override fun resolve(): PsiElement? {
    val file = element.containingFile as? NovaPsiFile ?: return null
    val sourceFile = file.virtualFile ?: return null
    val name = element.text
    val text = file.text

    val imported = resolveImportedSymbol(element.project, sourceFile, text, name)
    if (imported != null) return imported

    return resolveLocalDeclaration(file, text, name, element.textOffset)
  }

  override fun getVariants(): Array<Any> = emptyArray()
}

private fun resolveImportedSymbol(
  project: Project,
  sourceFile: VirtualFile,
  text: String,
  name: String,
): PsiElement? {
  for (match in NAMED_IMPORT_PATTERN.findAll(text)) {
    val source = match.groupValues[2]
    for (specifier in splitImportSpecifiers(match.groupValues[1])) {
      val importedName = specifier.substringBefore(" as ").trim()
      val localName = specifier.substringAfter(" as ", importedName).trim()
      if (localName != name) continue

      val targetFile = resolveImportFile(sourceFile, source) ?: return null
      return findExportedSymbol(project, targetFile, importedName) ?: psiFile(project, targetFile)
    }
  }

  for (match in DEFAULT_IMPORT_PATTERN.findAll(text)) {
    if (match.groupValues[1] != name) continue

    val targetFile = resolveImportFile(sourceFile, match.groupValues[2]) ?: return null
    return findDefaultExport(project, targetFile) ?: psiFile(project, targetFile)
  }

  for (match in NAMESPACE_IMPORT_PATTERN.findAll(text)) {
    if (match.groupValues[1] != name) continue

    val targetFile = resolveImportFile(sourceFile, match.groupValues[2]) ?: return null
    return psiFile(project, targetFile)
  }

  return null
}

private fun resolveLocalDeclaration(
  file: PsiFile,
  text: String,
  name: String,
  usageOffset: Int,
): PsiElement? {
  val scriptTextRange = scriptSetupRange(text) ?: TextRange(0, text.length)
  val script = scriptTextRange.substring(text)
  for (pattern in localDeclarationPatterns(name)) {
    val match = pattern.find(script) ?: continue
    val nameOffset = scriptTextRange.startOffset + match.range.first + match.value.lastIndexOf(name)
    if (nameOffset == usageOffset) return null
    return file.findElementAt(nameOffset)
  }

  return null
}

private fun findExportedSymbol(project: Project, targetFile: VirtualFile, name: String): PsiElement? {
  val psiFile = psiFile(project, targetFile) ?: return null
  val text = psiFile.text

  for (pattern in exportedDeclarationPatterns(name)) {
    val match = pattern.find(text) ?: continue
    val nameOffset = match.range.first + match.value.lastIndexOf(name)
    return psiFile.findElementAt(nameOffset) ?: psiFile
  }

  val fallback = Regex("""\b${Regex.escape(name)}\b""").find(text) ?: return null
  return psiFile.findElementAt(fallback.range.first) ?: psiFile
}

private fun findDefaultExport(project: Project, targetFile: VirtualFile): PsiElement? {
  val psiFile = psiFile(project, targetFile) ?: return null
  val text = psiFile.text
  val match = DEFAULT_EXPORT_PATTERN.find(text) ?: return null
  val name = match.groups["name"] ?: return psiFile.findElementAt(match.range.first) ?: psiFile
  return psiFile.findElementAt(name.range.first) ?: psiFile
}

private fun resolveImportFile(sourceFile: VirtualFile, rawPath: String): VirtualFile? {
  val path = rawPath.substringBefore('?')
  val parent = if (sourceFile.isDirectory) sourceFile else sourceFile.parent ?: return null
  val direct = VfsUtil.findRelativeFile(path, parent)
  if (direct != null && !direct.isDirectory) return direct

  for (candidate in importCandidates(path)) {
    val file = VfsUtil.findRelativeFile(candidate, parent)
    if (file != null && !file.isDirectory) return file
  }

  return null
}

private fun importCandidates(path: String): List<String> {
  val hasExtension = path.substringAfterLast('/', path).contains('.')
  if (hasExtension) return emptyList()

  return IMPORT_EXTENSIONS.map { "$path$it" } + IMPORT_EXTENSIONS.map { "$path/index$it" }
}

private fun psiFile(project: Project, file: VirtualFile): PsiFile? {
  return PsiManager.getInstance(project).findFile(file)
}

private fun scriptSetupRange(text: String): TextRange? {
  val open = SCRIPT_SETUP_OPEN_PATTERN.find(text) ?: return null
  val contentStart = open.range.last + 1
  val close = SCRIPT_CLOSE_PATTERN.find(text, contentStart) ?: return TextRange(contentStart, text.length)
  return TextRange(contentStart, close.range.first)
}

private fun splitImportSpecifiers(source: String): List<String> {
  return source
    .split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() && it != "type" }
    .map { it.removePrefix("type ").trim() }
}

private fun localDeclarationPatterns(name: String): List<Regex> {
  val escaped = Regex.escape(name)
  return listOf(
    Regex("""\b(?:const|let|var|function|class|interface|type|enum)\s+$escaped\b"""),
    Regex("""\b$escaped\s*=\s*(?:defineProps|defineEmits|Nova\.)"""),
  )
}

private fun exportedDeclarationPatterns(name: String): List<Regex> {
  val escaped = Regex.escape(name)
  return listOf(
    Regex("""\bexport\s+(?:async\s+)?function\s+$escaped\b"""),
    Regex("""\bexport\s+(?:const|let|var|class|interface|type|enum)\s+$escaped\b"""),
    Regex("""\b(?:const|let|var|function|class|interface|type|enum)\s+$escaped\b[\s\S]*?\bexport\s*\{[^}]*\b$escaped\b"""),
  )
}

private fun isIdentifier(value: String): Boolean {
  if (value.isEmpty()) return false
  if (!value.first().isLetter() && value.first() != '_' && value.first() != '$') return false
  return value.all { it.isLetterOrDigit() || it == '_' || it == '$' }
}

private val NAMED_IMPORT_PATTERN = Regex(
  """import\s+(?:type\s+)?\{([^}]+)}\s+from\s+['"]([^'"]+)['"]""",
)
private val DEFAULT_IMPORT_PATTERN = Regex(
  """import\s+([A-Za-z_$][\w$]*)\s+from\s+['"]([^'"]+)['"]""",
)
private val NAMESPACE_IMPORT_PATTERN = Regex(
  """import\s+\*\s+as\s+([A-Za-z_$][\w$]*)\s+from\s+['"]([^'"]+)['"]""",
)
private val DEFAULT_EXPORT_PATTERN = Regex(
  """\bexport\s+default\s+(?:(?:async\s+)?function|class)?\s*(?<name>[A-Za-z_$][\w$]*)?""",
)
private val SCRIPT_SETUP_OPEN_PATTERN = Regex("""<script\b[^>]*\bsetup\b[^>]*>""", RegexOption.IGNORE_CASE)
private val SCRIPT_CLOSE_PATTERN = Regex("""</script\s*>""", RegexOption.IGNORE_CASE)
private val IMPORT_EXTENSIONS = listOf(".ts", ".tsx", ".js", ".jsx", ".mjs", ".mts", ".nova")
