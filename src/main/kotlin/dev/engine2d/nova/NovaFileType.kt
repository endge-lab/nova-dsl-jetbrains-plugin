package dev.engine2d.nova

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class NovaFileType private constructor() : LanguageFileType(NovaLanguage) {
  override fun getName(): String = "Nova DSL"

  override fun getDescription(): String = "Nova DSL component"

  override fun getDefaultExtension(): String = "nova"

  override fun getIcon(): Icon = IconLoader.getIcon("/icons/nova-heart-16.png", NovaFileType::class.java)

  companion object {
    @JvmField
    val INSTANCE = NovaFileType()
  }
}
