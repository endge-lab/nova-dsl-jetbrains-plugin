package dev.engine2d.nova

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class NovaCssColorSettingsPage : ColorSettingsPage {
  override fun getIcon(): Icon? = NovaCssFileType.INSTANCE.icon

  override fun getHighlighter(): SyntaxHighlighter = NovaCssSyntaxHighlighter()

  override fun getDemoText(): String = """
    #timeline-example-title {
      color: var(--nova-example-header-title, #152235);
      borderRadius: 6;
    }

    @theme light {
      --nova-example-app-bg: #eef3f7;
      --nova-example-theme-button-border: rgba(37, 52, 69, 0.18);
    }
  """.trimIndent()

  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

  override fun getDisplayName(): String = "NovaCSS"

  companion object {
    private val DESCRIPTORS = arrayOf(
      AttributesDescriptor("Comment", NovaCssSyntaxHighlighter.COMMENT),
      AttributesDescriptor("At-rule", NovaCssSyntaxHighlighter.AT_RULE),
      AttributesDescriptor("Selector", NovaCssSyntaxHighlighter.SELECTOR),
      AttributesDescriptor("Property", NovaCssSyntaxHighlighter.PROPERTY),
      AttributesDescriptor("Custom property", NovaCssSyntaxHighlighter.CUSTOM_PROPERTY),
      AttributesDescriptor("Function", NovaCssSyntaxHighlighter.FUNCTION),
      AttributesDescriptor("Color", NovaCssSyntaxHighlighter.COLOR),
      AttributesDescriptor("String", NovaCssSyntaxHighlighter.STRING),
      AttributesDescriptor("Number", NovaCssSyntaxHighlighter.NUMBER),
      AttributesDescriptor("Identifier", NovaCssSyntaxHighlighter.IDENTIFIER),
      AttributesDescriptor("Operator", NovaCssSyntaxHighlighter.OPERATOR),
      AttributesDescriptor("Brace", NovaCssSyntaxHighlighter.BRACE),
    )
  }
}
