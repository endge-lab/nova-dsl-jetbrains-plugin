package dev.engine2d.nova

import com.intellij.psi.tree.IElementType

object NovaTokenTypes {
  val COMMENT = IElementType("NOVA_COMMENT", NovaLanguage)
  val TAG = IElementType("NOVA_TAG", NovaLanguage)
  val TAG_NAME = IElementType("NOVA_TAG_NAME", NovaLanguage)
  val ATTRIBUTE = IElementType("NOVA_ATTRIBUTE", NovaLanguage)
  val STRING = IElementType("NOVA_STRING", NovaLanguage)
  val NUMBER = IElementType("NOVA_NUMBER", NovaLanguage)
  val IDENTIFIER = IElementType("NOVA_IDENTIFIER", NovaLanguage)
  val KEYWORD = IElementType("NOVA_KEYWORD", NovaLanguage)
  val OPERATOR = IElementType("NOVA_OPERATOR", NovaLanguage)
  val BRACE = IElementType("NOVA_BRACE", NovaLanguage)
  val TEXT = IElementType("NOVA_TEXT", NovaLanguage)
}
