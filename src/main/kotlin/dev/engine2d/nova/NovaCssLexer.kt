package dev.engine2d.nova

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class NovaCssLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var startOffset = 0
  private var endOffset = 0
  private var tokenStart = 0
  private var tokenEnd = 0
  private var tokenType: IElementType? = null
  private var inBlock = false
  private var expectingProperty = false
  private var inValue = false

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.startOffset = startOffset
    this.endOffset = endOffset
    this.tokenStart = startOffset
    this.tokenEnd = startOffset
    this.inBlock = initialState and FLAG_IN_BLOCK != 0
    this.expectingProperty = initialState and FLAG_EXPECTING_PROPERTY != 0
    this.inValue = initialState and FLAG_IN_VALUE != 0
    locateToken()
  }

  override fun getState(): Int {
    var state = STATE_TOP_LEVEL
    if (inBlock) state = state or FLAG_IN_BLOCK
    if (expectingProperty) state = state or FLAG_EXPECTING_PROPERTY
    if (inValue) state = state or FLAG_IN_VALUE
    return state
  }

  override fun getTokenType(): IElementType? = tokenType

  override fun getTokenStart(): Int = tokenStart

  override fun getTokenEnd(): Int = tokenEnd

  override fun advance() {
    tokenStart = tokenEnd
    locateToken()
  }

  override fun getBufferSequence(): CharSequence = buffer

  override fun getBufferEnd(): Int = endOffset

  private fun locateToken() {
    if (tokenStart >= endOffset) {
      tokenType = null
      tokenEnd = endOffset
      return
    }

    val current = buffer[tokenStart]
    when {
      current.isWhitespace() -> {
        tokenType = TokenType.WHITE_SPACE
        tokenEnd = consumeWhile(tokenStart) { it.isWhitespace() }
      }
      startsWith("/*", tokenStart) -> {
        tokenType = NovaCssTokenTypes.COMMENT
        tokenEnd = indexAfter("*/", tokenStart + 2)
      }
      current == '{' -> {
        inBlock = true
        expectingProperty = true
        inValue = false
        tokenType = NovaCssTokenTypes.BRACE
        tokenEnd = tokenStart + 1
      }
      current == '}' -> {
        inBlock = false
        expectingProperty = false
        inValue = false
        tokenType = NovaCssTokenTypes.BRACE
        tokenEnd = tokenStart + 1
      }
      current == ':' -> {
        if (inBlock) {
          expectingProperty = false
          inValue = true
        }
        tokenType = NovaCssTokenTypes.OPERATOR
        tokenEnd = tokenStart + 1
      }
      current == ';' -> {
        if (inBlock) {
          expectingProperty = true
          inValue = false
        }
        tokenType = NovaCssTokenTypes.OPERATOR
        tokenEnd = tokenStart + 1
      }
      current in "(),[]>+~=|" -> {
        tokenType = NovaCssTokenTypes.OPERATOR
        tokenEnd = tokenStart + 1
      }
      current == '"' || current == '\'' -> {
        tokenType = NovaCssTokenTypes.STRING
        tokenEnd = consumeString(tokenStart, current)
      }
      current == '@' -> {
        tokenType = NovaCssTokenTypes.AT_RULE
        tokenEnd = consumeWhile(tokenStart + 1) { isIdentifierPart(it) }.coerceAtLeast(tokenStart + 1)
      }
      startsWith("--", tokenStart) -> {
        tokenEnd = consumeCssName(tokenStart)
        tokenType = NovaCssTokenTypes.CUSTOM_PROPERTY
      }
      current == '#' -> {
        tokenEnd = consumeHash(tokenStart)
        tokenType = if (inValue && isHexColor(tokenStart, tokenEnd)) {
          NovaCssTokenTypes.COLOR
        } else {
          NovaCssTokenTypes.SELECTOR
        }
      }
      current == '.' -> {
        tokenEnd = consumeCssName(tokenStart)
        tokenType = NovaCssTokenTypes.SELECTOR
      }
      current.isDigit() || (current == '-' && tokenStart + 1 < endOffset && buffer[tokenStart + 1].isDigit()) -> {
        tokenType = NovaCssTokenTypes.NUMBER
        tokenEnd = consumeNumberWithUnit(tokenStart)
      }
      isIdentifierStart(current) || current == '-' -> {
        tokenEnd = consumeCssName(tokenStart)
        tokenType = when {
          inBlock && expectingProperty -> NovaCssTokenTypes.PROPERTY
          nextNonSpace(tokenEnd) == '(' -> NovaCssTokenTypes.FUNCTION
          !inBlock -> NovaCssTokenTypes.SELECTOR
          else -> NovaCssTokenTypes.IDENTIFIER
        }
      }
      else -> {
        tokenType = NovaCssTokenTypes.TEXT
        tokenEnd = tokenStart + 1
      }
    }
  }

  private fun consumeWhile(offset: Int, predicate: (Char) -> Boolean): Int {
    var index = offset
    while (index < endOffset && predicate(buffer[index])) index += 1
    return index
  }

  private fun consumeString(offset: Int, quote: Char): Int {
    var index = offset + 1
    var escaped = false
    while (index < endOffset) {
      val current = buffer[index]
      if (escaped) {
        escaped = false
      } else if (current == '\\') {
        escaped = true
      } else if (current == quote) {
        return index + 1
      }
      index += 1
    }
    return endOffset
  }

  private fun consumeCssName(offset: Int): Int {
    var index = offset
    while (index < endOffset && isCssNamePart(buffer[index])) index += 1
    return index
  }

  private fun consumeHash(offset: Int): Int {
    var index = offset + 1
    while (index < endOffset && isCssNamePart(buffer[index])) index += 1
    return index
  }

  private fun consumeNumberWithUnit(offset: Int): Int {
    var index = offset
    if (index < endOffset && buffer[index] == '-') index += 1
    var hasDot = false
    while (index < endOffset) {
      val current = buffer[index]
      if (current.isDigit()) {
        index += 1
      } else if (current == '.' && !hasDot) {
        hasDot = true
        index += 1
      } else {
        break
      }
    }
    while (index < endOffset && (buffer[index].isLetter() || buffer[index] == '%')) index += 1
    return index
  }

  private fun indexAfter(value: String, searchStart: Int): Int {
    var index = searchStart
    while (index + value.length <= endOffset) {
      if (startsWith(value, index)) return index + value.length
      index += 1
    }
    return endOffset
  }

  private fun startsWith(value: String, offset: Int): Boolean {
    if (offset + value.length > endOffset) return false
    for (index in value.indices) {
      if (buffer[offset + index] != value[index]) return false
    }
    return true
  }

  private fun nextNonSpace(offset: Int): Char? {
    var index = offset
    while (index < endOffset && buffer[index].isWhitespace()) index += 1
    return if (index < endOffset) buffer[index] else null
  }

  private fun isHexColor(start: Int, end: Int): Boolean {
    val length = end - start - 1
    if (length !in setOf(3, 4, 6, 8)) return false
    for (index in start + 1 until end) {
      if (!buffer[index].isDigit() && buffer[index].lowercaseChar() !in 'a'..'f') return false
    }
    return true
  }

  private fun isIdentifierStart(value: Char): Boolean = value.isLetter() || value == '_'

  private fun isIdentifierPart(value: Char): Boolean = value.isLetterOrDigit() || value == '_' || value == '-'

  private fun isCssNamePart(value: Char): Boolean {
    return value.isLetterOrDigit() || value == '_' || value == '-' || value == '.' || value == '#'
  }

  companion object {
    private const val STATE_TOP_LEVEL = 0
    private const val FLAG_IN_BLOCK = 1
    private const val FLAG_EXPECTING_PROPERTY = 1 shl 1
    private const val FLAG_IN_VALUE = 1 shl 2
  }
}
