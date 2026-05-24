package dev.engine2d.nova

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class NovaCssSyntaxHighlighter : SyntaxHighlighter {
  override fun getHighlightingLexer(): Lexer = NovaCssLexer()

  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
    NovaCssTokenTypes.COMMENT -> COMMENT_KEYS
    NovaCssTokenTypes.AT_RULE -> AT_RULE_KEYS
    NovaCssTokenTypes.SELECTOR -> SELECTOR_KEYS
    NovaCssTokenTypes.PROPERTY -> PROPERTY_KEYS
    NovaCssTokenTypes.CUSTOM_PROPERTY -> CUSTOM_PROPERTY_KEYS
    NovaCssTokenTypes.FUNCTION -> FUNCTION_KEYS
    NovaCssTokenTypes.COLOR -> COLOR_KEYS
    NovaCssTokenTypes.STRING -> STRING_KEYS
    NovaCssTokenTypes.NUMBER -> NUMBER_KEYS
    NovaCssTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
    NovaCssTokenTypes.OPERATOR -> OPERATOR_KEYS
    NovaCssTokenTypes.BRACE -> BRACE_KEYS
    TokenType.BAD_CHARACTER -> BAD_CHARACTER_KEYS
    else -> EMPTY_KEYS
  }

  companion object {
    val COMMENT = TextAttributesKey.createTextAttributesKey("NOVACSS_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    val AT_RULE = TextAttributesKey.createTextAttributesKey("NOVACSS_AT_RULE", DefaultLanguageHighlighterColors.METADATA)
    val SELECTOR = TextAttributesKey.createTextAttributesKey("NOVACSS_SELECTOR", DefaultLanguageHighlighterColors.CLASS_NAME)
    val PROPERTY = TextAttributesKey.createTextAttributesKey("NOVACSS_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val CUSTOM_PROPERTY = TextAttributesKey.createTextAttributesKey("NOVACSS_CUSTOM_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val FUNCTION = TextAttributesKey.createTextAttributesKey("NOVACSS_FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_CALL)
    val COLOR = TextAttributesKey.createTextAttributesKey("NOVACSS_COLOR", DefaultLanguageHighlighterColors.STRING)
    val STRING = TextAttributesKey.createTextAttributesKey("NOVACSS_STRING", DefaultLanguageHighlighterColors.STRING)
    val NUMBER = TextAttributesKey.createTextAttributesKey("NOVACSS_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val IDENTIFIER = TextAttributesKey.createTextAttributesKey("NOVACSS_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val OPERATOR = TextAttributesKey.createTextAttributesKey("NOVACSS_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val BRACE = TextAttributesKey.createTextAttributesKey("NOVACSS_BRACE", DefaultLanguageHighlighterColors.BRACES)
    val BAD_CHARACTER = TextAttributesKey.createTextAttributesKey("NOVACSS_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    private val COMMENT_KEYS = arrayOf(COMMENT)
    private val AT_RULE_KEYS = arrayOf(AT_RULE)
    private val SELECTOR_KEYS = arrayOf(SELECTOR)
    private val PROPERTY_KEYS = arrayOf(PROPERTY)
    private val CUSTOM_PROPERTY_KEYS = arrayOf(CUSTOM_PROPERTY)
    private val FUNCTION_KEYS = arrayOf(FUNCTION)
    private val COLOR_KEYS = arrayOf(COLOR)
    private val STRING_KEYS = arrayOf(STRING)
    private val NUMBER_KEYS = arrayOf(NUMBER)
    private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
    private val OPERATOR_KEYS = arrayOf(OPERATOR)
    private val BRACE_KEYS = arrayOf(BRACE)
    private val BAD_CHARACTER_KEYS = arrayOf(BAD_CHARACTER)
    private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
  }
}
