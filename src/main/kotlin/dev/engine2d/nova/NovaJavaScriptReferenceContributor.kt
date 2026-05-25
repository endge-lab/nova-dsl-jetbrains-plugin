package dev.engine2d.nova

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext

class NovaJavaScriptReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    if (!isJavaScriptPluginAvailable()) return

    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(NovaTokenTypes.IDENTIFIER),
      NovaJavaScriptReferenceProvider(),
    )
  }
}

private class NovaJavaScriptReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(
    element: PsiElement,
    context: ProcessingContext,
  ): Array<PsiReference> {
    if (element.containingFile !is NovaPsiFile) return PsiReference.EMPTY_ARRAY
    if (element.elementType != NovaTokenTypes.IDENTIFIER) return PsiReference.EMPTY_ARRAY

    // The optional descriptor keeps JavaScript-related extension loading isolated.
    // Reference resolution itself delegates to NovaJavaScriptPsiBridge from the base provider.
    return PsiReference.EMPTY_ARRAY
  }
}

object NovaJavaScriptPsiBridge {
  fun findNamedElement(file: PsiFile, name: String): PsiElement? {
    if (!isJavaScriptPluginAvailable()) return null
    return PsiTreeUtil
      .findChildrenOfType(file, PsiNamedElement::class.java)
      .firstOrNull { it.name == name }
      ?.navigationElement
  }
}

private fun isJavaScriptPluginAvailable(): Boolean {
  return runCatching {
    Class.forName("com.intellij.lang.javascript.psi.JSFile", false, NovaJavaScriptReferenceContributor::class.java.classLoader)
  }.isSuccess
}
