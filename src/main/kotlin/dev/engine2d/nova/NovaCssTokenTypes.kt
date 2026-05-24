package dev.engine2d.nova

import com.intellij.psi.tree.IElementType

object NovaCssTokenTypes {
  val COMMENT = IElementType("NOVACSS_COMMENT", NovaCssLanguage)
  val AT_RULE = IElementType("NOVACSS_AT_RULE", NovaCssLanguage)
  val SELECTOR = IElementType("NOVACSS_SELECTOR", NovaCssLanguage)
  val PROPERTY = IElementType("NOVACSS_PROPERTY", NovaCssLanguage)
  val CUSTOM_PROPERTY = IElementType("NOVACSS_CUSTOM_PROPERTY", NovaCssLanguage)
  val FUNCTION = IElementType("NOVACSS_FUNCTION", NovaCssLanguage)
  val COLOR = IElementType("NOVACSS_COLOR", NovaCssLanguage)
  val STRING = IElementType("NOVACSS_STRING", NovaCssLanguage)
  val NUMBER = IElementType("NOVACSS_NUMBER", NovaCssLanguage)
  val IDENTIFIER = IElementType("NOVACSS_IDENTIFIER", NovaCssLanguage)
  val OPERATOR = IElementType("NOVACSS_OPERATOR", NovaCssLanguage)
  val BRACE = IElementType("NOVACSS_BRACE", NovaCssLanguage)
  val TEXT = IElementType("NOVACSS_TEXT", NovaCssLanguage)
}
