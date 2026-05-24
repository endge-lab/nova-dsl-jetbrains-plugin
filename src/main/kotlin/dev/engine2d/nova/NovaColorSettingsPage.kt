package dev.engine2d.nova

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class NovaColorSettingsPage : ColorSettingsPage {
  override fun getIcon(): Icon? = NovaFileType.INSTANCE.icon

  override fun getHighlighter(): SyntaxHighlighter = NovaSyntaxHighlighter()

  override fun getDemoText(): String = """
    <script setup lang="ts">
    import { Nova } from '@endge/nova'

    const activeTheme = Nova.signal('light')
    const props = defineProps()
    </script>

    <template>
      <Root :width="props.width" :height="props.height">
        <TextBlock text="Nova DSL" color="#2563eb" />
      </Root>
    </template>

    <style lang="novacss">
    @theme light {
      --nova-scene-bg: #ffffff;
    }
    </style>
  """.trimIndent()

  override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

  override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

  override fun getDisplayName(): String = "Nova DSL"

  companion object {
    private val DESCRIPTORS = arrayOf(
      AttributesDescriptor("Comment", NovaSyntaxHighlighter.COMMENT),
      AttributesDescriptor("Tag", NovaSyntaxHighlighter.TAG),
      AttributesDescriptor("Tag name", NovaSyntaxHighlighter.TAG_NAME),
      AttributesDescriptor("Attribute", NovaSyntaxHighlighter.ATTRIBUTE),
      AttributesDescriptor("String", NovaSyntaxHighlighter.STRING),
      AttributesDescriptor("Keyword", NovaSyntaxHighlighter.KEYWORD),
      AttributesDescriptor("Operator", NovaSyntaxHighlighter.OPERATOR),
      AttributesDescriptor("Brace", NovaSyntaxHighlighter.BRACE),
    )
  }
}
