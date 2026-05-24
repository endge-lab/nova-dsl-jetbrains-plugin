package dev.engine2d.nova

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class NovaPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NovaLanguage) {
  override fun getFileType() = NovaFileType.INSTANCE

  override fun toString(): String = "Nova DSL File"
}
