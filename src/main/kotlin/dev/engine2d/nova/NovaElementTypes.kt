package dev.engine2d.nova

import com.intellij.psi.tree.IFileElementType

object NovaElementTypes {
  val FILE = IFileElementType(NovaLanguage)
  val CSS_FILE = IFileElementType(NovaCssLanguage)
}
