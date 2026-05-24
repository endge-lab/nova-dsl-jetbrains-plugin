package dev.engine2d.nova

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class NovaCssFileType private constructor() : LanguageFileType(NovaCssLanguage) {
  override fun getName(): String = "NovaCSS"

  override fun getDescription(): String = "NovaCSS stylesheet"

  override fun getDefaultExtension(): String = "novacss"

  override fun getIcon(): Icon = IconLoader.getIcon("/icons/nova-heart-16.png", NovaCssFileType::class.java)

  companion object {
    @JvmField
    val INSTANCE = NovaCssFileType()
  }
}
