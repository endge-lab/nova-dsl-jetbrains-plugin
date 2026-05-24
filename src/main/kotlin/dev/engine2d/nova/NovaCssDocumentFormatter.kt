package dev.engine2d.nova

internal object NovaCssDocumentFormatter {
  fun format(source: String): String {
    if (source.isEmpty()) return source

    val lineSeparator = if (source.contains("\r\n")) "\r\n" else "\n"
    val result = StringBuilder(source.length + 32)
    val current = StringBuilder()
    var index = 0
    var indent = 0
    var parenDepth = 0

    fun emitLine(content: String, indentLevel: Int) {
      val trimmed = content.trim()
      if (trimmed.isEmpty()) return
      repeat(indentLevel.coerceAtLeast(0)) { result.append(INDENT) }
      result.append(trimmed).append(lineSeparator)
    }

    fun emitBlankLine() {
      val text = result.toString()
      if (text.isNotEmpty() && !text.endsWith(lineSeparator + lineSeparator)) {
        result.append(lineSeparator)
      }
    }

    fun flushCurrent() {
      emitLine(current.toString(), indent)
      current.clear()
    }

    fun appendSpaceIfNeeded() {
      if (current.isNotEmpty() && !current.last().isWhitespace() && current.last() !in "({[,") {
        current.append(' ')
      }
    }

    while (index < source.length) {
      when {
        source.startsWith("/*", index) -> {
          val end = source.indexOf("*/", index + 2).let { if (it == -1) source.length else it + 2 }
          val comment = source.substring(index, end)
          if (current.isBlank()) {
            emitLine(comment, indent)
          } else {
            appendSpaceIfNeeded()
            current.append(comment)
          }
          index = end
        }

        source[index] == '"' || source[index] == '\'' -> {
          val end = consumeString(source, index, source[index])
          current.append(source, index, end)
          index = end
        }

        source[index] == '(' -> {
          parenDepth += 1
          current.append(source[index])
          index += 1
        }

        source[index] == ')' -> {
          parenDepth = (parenDepth - 1).coerceAtLeast(0)
          current.append(source[index])
          index += 1
        }

        source[index] == '{' && parenDepth == 0 -> {
          val header = current.toString().trim()
          emitLine(if (header.isEmpty()) "{" else "$header {", indent)
          current.clear()
          indent += 1
          index += 1
        }

        source[index] == '}' && parenDepth == 0 -> {
          flushCurrent()
          indent = (indent - 1).coerceAtLeast(0)
          emitLine("}", indent)
          current.clear()
          index += 1

          if (indent == 0 && nextNonWhitespace(source, index) != null) {
            emitBlankLine()
          }
        }

        source[index] == ';' && parenDepth == 0 -> {
          current.append(';')
          flushCurrent()
          index += 1
        }

        source[index].isWhitespace() -> {
          if (current.isNotBlank()) appendSpaceIfNeeded()
          index += 1
        }

        else -> {
          current.append(source[index])
          index += 1
        }
      }
    }

    flushCurrent()
    return trimTrailingLineSeparators(result.toString(), lineSeparator, source.endsWithLineSeparator())
  }

  private fun consumeString(source: String, start: Int, quote: Char): Int {
    var index = start + 1
    var escaped = false
    while (index < source.length) {
      val current = source[index]
      if (escaped) {
        escaped = false
      } else if (current == '\\') {
        escaped = true
      } else if (current == quote) {
        return index + 1
      }
      index += 1
    }
    return source.length
  }

  private fun nextNonWhitespace(source: String, start: Int): Char? {
    var index = start
    while (index < source.length) {
      val current = source[index]
      if (!current.isWhitespace()) return current
      index += 1
    }
    return null
  }

  private fun trimTrailingLineSeparators(value: String, lineSeparator: String, keepFinalLineSeparator: Boolean): String {
    var result = value
    while (result.endsWith(lineSeparator)) {
      result = result.dropLast(lineSeparator.length)
    }
    return if (keepFinalLineSeparator && result.isNotEmpty()) result + lineSeparator else result
  }

  private fun String.endsWithLineSeparator(): Boolean = endsWith('\n') || endsWith('\r')

  private const val INDENT = "  "
}
