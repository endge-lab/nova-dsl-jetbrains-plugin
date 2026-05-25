package dev.engine2d.nova

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class NovaCssPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NovaCssLanguage) {
  override fun getFileType() = NovaCssFileType.INSTANCE

  override fun toString(): String = "NovaCSS File"
}
