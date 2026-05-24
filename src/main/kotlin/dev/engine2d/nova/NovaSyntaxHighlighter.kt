package dev.engine2d.nova

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class NovaSyntaxHighlighter : SyntaxHighlighter {
  override fun getHighlightingLexer(): Lexer = NovaLexer()

  override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
    NovaTokenTypes.COMMENT -> COMMENT_KEYS
    NovaTokenTypes.TAG -> TAG_KEYS
    NovaTokenTypes.TAG_NAME -> TAG_NAME_KEYS
    NovaTokenTypes.ATTRIBUTE -> ATTRIBUTE_KEYS
    NovaTokenTypes.STRING -> STRING_KEYS
    NovaTokenTypes.NUMBER -> NUMBER_KEYS
    NovaTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
    NovaTokenTypes.KEYWORD -> KEYWORD_KEYS
    NovaTokenTypes.OPERATOR -> OPERATOR_KEYS
    NovaTokenTypes.BRACE -> BRACE_KEYS
    TokenType.BAD_CHARACTER -> BAD_CHARACTER_KEYS
    else -> EMPTY_KEYS
  }

  companion object {
    val COMMENT = TextAttributesKey.createTextAttributesKey("NOVA_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    val TAG = TextAttributesKey.createTextAttributesKey("NOVA_TAG", DefaultLanguageHighlighterColors.MARKUP_TAG)
    val TAG_NAME = TextAttributesKey.createTextAttributesKey("NOVA_TAG_NAME", DefaultLanguageHighlighterColors.MARKUP_TAG)
    val ATTRIBUTE = TextAttributesKey.createTextAttributesKey("NOVA_ATTRIBUTE", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE)
    val STRING = TextAttributesKey.createTextAttributesKey("NOVA_STRING", DefaultLanguageHighlighterColors.STRING)
    val NUMBER = TextAttributesKey.createTextAttributesKey("NOVA_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val IDENTIFIER = TextAttributesKey.createTextAttributesKey("NOVA_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val KEYWORD = TextAttributesKey.createTextAttributesKey("NOVA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val OPERATOR = TextAttributesKey.createTextAttributesKey("NOVA_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val BRACE = TextAttributesKey.createTextAttributesKey("NOVA_BRACE", DefaultLanguageHighlighterColors.BRACES)
    val BAD_CHARACTER = TextAttributesKey.createTextAttributesKey("NOVA_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    private val COMMENT_KEYS = arrayOf(COMMENT)
    private val TAG_KEYS = arrayOf(TAG)
    private val TAG_NAME_KEYS = arrayOf(TAG_NAME)
    private val ATTRIBUTE_KEYS = arrayOf(ATTRIBUTE)
    private val STRING_KEYS = arrayOf(STRING)
    private val NUMBER_KEYS = arrayOf(NUMBER)
    private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
    private val KEYWORD_KEYS = arrayOf(KEYWORD)
    private val OPERATOR_KEYS = arrayOf(OPERATOR)
    private val BRACE_KEYS = arrayOf(BRACE)
    private val BAD_CHARACTER_KEYS = arrayOf(BAD_CHARACTER)
    private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
  }
}
