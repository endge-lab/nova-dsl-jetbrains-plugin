package dev.engine2d.nova

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class NovaLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var startOffset = 0
  private var endOffset = 0
  private var tokenStart = 0
  private var tokenEnd = 0
  private var tokenType: IElementType? = null
  private var inTag = false
  private var afterTagOpen = false

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.startOffset = startOffset
    this.endOffset = endOffset
    this.tokenStart = startOffset
    this.tokenEnd = startOffset
    this.inTag = initialState == STATE_IN_TAG
    this.afterTagOpen = false
    locateToken()
  }

  override fun getState(): Int = if (inTag) STATE_IN_TAG else STATE_TEXT

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
    if (current.isWhitespace()) {
      tokenType = TokenType.WHITE_SPACE
      tokenEnd = consumeWhile(tokenStart) { it.isWhitespace() }
      return
    }

    if (startsWith("<!--", tokenStart)) {
      tokenType = NovaTokenTypes.COMMENT
      tokenEnd = indexAfter("-->", tokenStart + 4)
      return
    }

    if (inTag) {
      locateTagToken()
      return
    }

    when {
      current == '<' -> {
        inTag = true
        afterTagOpen = true
        tokenType = NovaTokenTypes.TAG
        tokenEnd = when {
          startsWith("</", tokenStart) -> tokenStart + 2
          else -> tokenStart + 1
        }
      }
      isBrace(current) -> {
        tokenType = NovaTokenTypes.BRACE
        tokenEnd = tokenStart + 1
      }
      current == '"' || current == '\'' || current == '`' -> {
        tokenType = NovaTokenTypes.STRING
        tokenEnd = consumeString(tokenStart, current)
      }
      isWordStart(current) -> {
        tokenEnd = consumeWhile(tokenStart) { isWordPart(it) || it == '-' || it == '.' || it == '@' }
        tokenType = if (isKeyword(buffer.subSequence(tokenStart, tokenEnd).toString())) {
          NovaTokenTypes.KEYWORD
        } else {
          NovaTokenTypes.TEXT
        }
      }
      else -> {
        tokenType = NovaTokenTypes.TEXT
        tokenEnd = tokenStart + 1
      }
    }
  }

  private fun locateTagToken() {
    val current = buffer[tokenStart]

    when {
      current == '>' -> {
        inTag = false
        afterTagOpen = false
        tokenType = NovaTokenTypes.TAG
        tokenEnd = tokenStart + 1
      }
      startsWith("/>", tokenStart) -> {
        inTag = false
        afterTagOpen = false
        tokenType = NovaTokenTypes.TAG
        tokenEnd = tokenStart + 2
      }
      current == '"' || current == '\'' || current == '`' -> {
        tokenType = NovaTokenTypes.STRING
        tokenEnd = consumeString(tokenStart, current)
      }
      current == '=' || current == ':' || current == '@' || current == '#' -> {
        tokenType = NovaTokenTypes.OPERATOR
        tokenEnd = tokenStart + 1
      }
      isBrace(current) -> {
        tokenType = NovaTokenTypes.BRACE
        tokenEnd = tokenStart + 1
      }
      isWordStart(current) -> {
        tokenEnd = consumeWhile(tokenStart) { isWordPart(it) || it == '-' || it == '.' }
        tokenType = if (afterTagOpen) NovaTokenTypes.TAG_NAME else NovaTokenTypes.ATTRIBUTE
        afterTagOpen = false
      }
      else -> {
        tokenType = NovaTokenTypes.TEXT
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

  private fun startsWith(value: String, offset: Int): Boolean {
    if (offset + value.length > endOffset) return false
    for (index in value.indices) {
      if (buffer[offset + index] != value[index]) return false
    }
    return true
  }

  private fun indexAfter(value: String, searchStart: Int): Int {
    var index = searchStart
    while (index + value.length <= endOffset) {
      if (startsWith(value, index)) return index + value.length
      index += 1
    }
    return endOffset
  }

  private fun isKeyword(value: String): Boolean = value in KEYWORDS

  private fun isWordStart(value: Char): Boolean = value.isLetter() || value == '_' || value == '$' || value == '@'

  private fun isWordPart(value: Char): Boolean = value.isLetterOrDigit() || value == '_' || value == '$'

  private fun isBrace(value: Char): Boolean = value == '{' || value == '}' || value == '(' || value == ')' || value == '[' || value == ']'

  companion object {
    private const val STATE_TEXT = 0
    private const val STATE_IN_TAG = 1

    private val KEYWORDS = setOf(
      "@theme",
      "const",
      "let",
      "var",
      "function",
      "import",
      "export",
      "from",
      "return",
      "if",
      "else",
      "for",
      "in",
      "of",
      "true",
      "false",
      "null",
      "undefined",
      "defineProps",
      "defineEmits",
      "Nova",
    )
  }
}
