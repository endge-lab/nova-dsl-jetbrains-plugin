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
  private var closingTag = false
  private var currentTagName: String? = null
  private var inScriptContent = false
  private var inAttributeExpression = false
  private var attributeExpressionQuote = 0.toChar()

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.startOffset = startOffset
    this.endOffset = endOffset
    this.tokenStart = startOffset
    this.tokenEnd = startOffset
    this.inTag = initialState and FLAG_IN_TAG != 0
    this.inScriptContent = initialState and FLAG_SCRIPT_CONTENT != 0
    this.inAttributeExpression = initialState and FLAG_ATTRIBUTE_EXPRESSION != 0
    this.attributeExpressionQuote = when {
      initialState and FLAG_EXPR_SINGLE_QUOTE != 0 -> '\''
      initialState and FLAG_EXPR_BACKTICK != 0 -> '`'
      else -> '"'
    }
    this.afterTagOpen = false
    this.closingTag = false
    this.currentTagName = null
    locateToken()
  }

  override fun getState(): Int {
    var state = STATE_TEXT
    if (inTag) state = state or FLAG_IN_TAG
    if (inScriptContent) state = state or FLAG_SCRIPT_CONTENT
    if (inAttributeExpression) {
      state = state or FLAG_ATTRIBUTE_EXPRESSION
      state = when (attributeExpressionQuote) {
        '\'' -> state or FLAG_EXPR_SINGLE_QUOTE
        '`' -> state or FLAG_EXPR_BACKTICK
        else -> state or FLAG_EXPR_DOUBLE_QUOTE
      }
    }
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

    if (inScriptContent) {
      if (startsWithIgnoreCase("</script", tokenStart)) {
        inScriptContent = false
      } else {
        locateCodeToken()
        return
      }
    }

    if (inTag) {
      locateTagToken()
      return
    }

    when {
      current == '<' -> {
        inTag = true
        afterTagOpen = true
        closingTag = startsWith("</", tokenStart)
        currentTagName = null
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

    if (inAttributeExpression) {
      locateCodeToken(attributeExpressionQuote)
      return
    }

    when {
      current == '>' -> {
        val shouldEnterScript = !closingTag && currentTagName == "script"
        inTag = false
        afterTagOpen = false
        closingTag = false
        currentTagName = null
        inScriptContent = shouldEnterScript
        tokenType = NovaTokenTypes.TAG
        tokenEnd = tokenStart + 1
      }
      startsWith("/>", tokenStart) -> {
        inTag = false
        afterTagOpen = false
        closingTag = false
        currentTagName = null
        tokenType = NovaTokenTypes.TAG
        tokenEnd = tokenStart + 2
      }
      current == '"' || current == '\'' || current == '`' -> {
        if (isBoundAttributeValue(tokenStart)) {
          inAttributeExpression = true
          attributeExpressionQuote = current
          tokenType = NovaTokenTypes.OPERATOR
          tokenEnd = tokenStart + 1
        } else {
          tokenType = NovaTokenTypes.STRING
          tokenEnd = consumeString(tokenStart, current)
        }
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
        if (afterTagOpen) {
          currentTagName = buffer.subSequence(tokenStart, tokenEnd).toString().lowercase()
          tokenType = NovaTokenTypes.TAG_NAME
        } else {
          tokenType = NovaTokenTypes.ATTRIBUTE
        }
        afterTagOpen = false
      }
      else -> {
        tokenType = NovaTokenTypes.TEXT
        tokenEnd = tokenStart + 1
      }
    }
  }

  private fun locateCodeToken(stopQuote: Char? = null) {
    val current = buffer[tokenStart]

    if (stopQuote != null && current == stopQuote) {
      inAttributeExpression = false
      attributeExpressionQuote = 0.toChar()
      tokenType = NovaTokenTypes.OPERATOR
      tokenEnd = tokenStart + 1
      return
    }

    when {
      startsWith("//", tokenStart) -> {
        tokenType = NovaTokenTypes.COMMENT
        tokenEnd = consumeUntilLineEnd(tokenStart + 2)
      }
      startsWith("/*", tokenStart) -> {
        tokenType = NovaTokenTypes.COMMENT
        tokenEnd = indexAfter("*/", tokenStart + 2)
      }
      current == '"' || current == '\'' || current == '`' -> {
        tokenType = NovaTokenTypes.STRING
        tokenEnd = consumeString(tokenStart, current)
      }
      current.isDigit() -> {
        tokenType = NovaTokenTypes.NUMBER
        tokenEnd = consumeNumber(tokenStart)
      }
      isBrace(current) -> {
        tokenType = NovaTokenTypes.BRACE
        tokenEnd = tokenStart + 1
      }
      isCodeOperator(current) -> {
        tokenType = NovaTokenTypes.OPERATOR
        tokenEnd = tokenStart + 1
      }
      isWordStart(current) -> {
        tokenEnd = consumeWhile(tokenStart) { isWordPart(it) }
        val word = buffer.subSequence(tokenStart, tokenEnd).toString()
        tokenType = if (isKeyword(word)) NovaTokenTypes.KEYWORD else NovaTokenTypes.IDENTIFIER
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

  private fun consumeNumber(offset: Int): Int {
    var index = offset
    var hasDot = false
    while (index < endOffset) {
      val current = buffer[index]
      if (current.isDigit() || current == '_') {
        index += 1
      } else if (current == '.' && !hasDot) {
        hasDot = true
        index += 1
      } else {
        break
      }
    }
    return index
  }

  private fun consumeUntilLineEnd(offset: Int): Int {
    var index = offset
    while (index < endOffset && buffer[index] != '\n' && buffer[index] != '\r') index += 1
    return index
  }

  private fun startsWith(value: String, offset: Int): Boolean {
    if (offset + value.length > endOffset) return false
    for (index in value.indices) {
      if (buffer[offset + index] != value[index]) return false
    }
    return true
  }

  private fun startsWithIgnoreCase(value: String, offset: Int): Boolean {
    if (offset + value.length > endOffset) return false
    for (index in value.indices) {
      if (!buffer[offset + index].equals(value[index], ignoreCase = true)) return false
    }
    return true
  }

  private fun isBoundAttributeValue(quoteOffset: Int): Boolean {
    var index = quoteOffset - 1
    while (index >= startOffset && buffer[index].isWhitespace()) index -= 1
    if (index < startOffset || buffer[index] != '=') return false

    index -= 1
    while (index >= startOffset && buffer[index].isWhitespace()) index -= 1
    val attributeEnd = index + 1
    while (index >= startOffset && isAttributeNamePart(buffer[index])) index -= 1
    val attributeName = buffer.subSequence(index + 1, attributeEnd).toString()

    return attributeName.startsWith(":") ||
      attributeName.startsWith("@") ||
      attributeName.startsWith("#") ||
      attributeName.startsWith("v-") ||
      attributeName in DIRECTIVE_ATTRIBUTES
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

  private fun isCodeOperator(value: Char): Boolean = value in "=+-*/%!?&|^~<>:;,.#"

  private fun isAttributeNamePart(value: Char): Boolean {
    return value.isLetterOrDigit() || value == '_' || value == '-' || value == '.' || value == ':' || value == '@' || value == '#'
  }

  companion object {
    private const val STATE_TEXT = 0
    private const val FLAG_IN_TAG = 1
    private const val FLAG_SCRIPT_CONTENT = 1 shl 1
    private const val FLAG_ATTRIBUTE_EXPRESSION = 1 shl 2
    private const val FLAG_EXPR_DOUBLE_QUOTE = 1 shl 3
    private const val FLAG_EXPR_SINGLE_QUOTE = 1 shl 4
    private const val FLAG_EXPR_BACKTICK = 1 shl 5

    private val KEYWORDS = setOf(
      "@theme",
      "as",
      "async",
      "await",
      "break",
      "case",
      "catch",
      "class",
      "const",
      "continue",
      "default",
      "delete",
      "do",
      "let",
      "var",
      "function",
      "import",
      "export",
      "extends",
      "finally",
      "from",
      "return",
      "if",
      "else",
      "for",
      "in",
      "interface",
      "new",
      "of",
      "switch",
      "this",
      "throw",
      "try",
      "type",
      "true",
      "false",
      "null",
      "undefined",
      "while",
      "defineProps",
      "defineEmits",
      "Nova",
    )

    private val DIRECTIVE_ATTRIBUTES = setOf(
      "v-if",
      "v-else-if",
      "v-for",
      "v-show",
      "v-bind",
      "v-on",
      "v-slot",
      "v-model",
      "key",
    )
  }
}
