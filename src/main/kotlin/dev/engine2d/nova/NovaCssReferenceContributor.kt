package dev.engine2d.nova

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext

class NovaCssReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(NovaCssTokenTypes.CUSTOM_PROPERTY),
      NovaCssCustomPropertyReferenceProvider(),
    )
  }
}

private class NovaCssCustomPropertyReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(
    element: PsiElement,
    context: ProcessingContext,
  ): Array<PsiReference> {
    if (element.containingFile !is NovaCssPsiFile) return PsiReference.EMPTY_ARRAY
    if (element.elementType != NovaCssTokenTypes.CUSTOM_PROPERTY) return PsiReference.EMPTY_ARRAY
    if (isDeclaration(element)) return PsiReference.EMPTY_ARRAY
    return arrayOf(NovaCssCustomPropertyReference(element))
  }

  private fun isDeclaration(element: PsiElement): Boolean {
    val text = element.containingFile.text
    var index = element.textRange.endOffset
    while (index < text.length && text[index].isWhitespace()) index += 1
    return index < text.length && text[index] == ':'
  }
}

private class NovaCssCustomPropertyReference(element: PsiElement) : PsiReferenceBase<PsiElement>(
  element,
  TextRange(0, element.textLength),
  false,
) {
  override fun resolve(): PsiElement? {
    val name = element.text
    val current = resolveInFile(element.containingFile, name, element.textOffset)
    if (current != null) return current

    val root = findCssRoot(element.containingFile.virtualFile) ?: return null
    return resolveInProject(root, name)
  }

  override fun getVariants(): Array<Any> = emptyArray()

  private fun resolveInProject(root: VirtualFile, name: String): PsiElement? {
    val files = mutableListOf<VirtualFile>()
    collectCssFiles(root, files, 0)
    for (file in files) {
      val psi = PsiManager.getInstance(element.project).findFile(file) ?: continue
      val resolved = resolveInFile(psi, name, element.textOffset)
      if (resolved != null) return resolved
    }
    return null
  }

  private fun resolveInFile(file: PsiElement, name: String, usageOffset: Int): PsiElement? {
    val text = file.containingFile.text
    val pattern = Regex("""(?m)${Regex.escape(name)}\s*:""")
    val match = pattern.find(text) ?: return null
    if (match.range.first == usageOffset) return null
    return file.containingFile.findElementAt(match.range.first)
  }

  private fun collectCssFiles(file: VirtualFile, result: MutableList<VirtualFile>, depth: Int) {
    if (result.size >= 600 || depth > 8) return
    if (file.isDirectory) {
      if (file.name in SKIPPED_REFERENCE_DIRS) return
      for (child in file.children.take(160)) collectCssFiles(child, result, depth + 1)
      return
    }
    if (file.extension == "novacss") result += file
  }
}

private fun findCssRoot(file: VirtualFile?): VirtualFile? {
  val start = file ?: return null
  return generateSequence(if (start.isDirectory) start else start.parent) { it.parent }
    .firstOrNull { it.findChild("package.json") != null }
}

private val SKIPPED_REFERENCE_DIRS = setOf(".git", ".gradle", ".idea", "build", "coverage", "dist", "node_modules", "out")
