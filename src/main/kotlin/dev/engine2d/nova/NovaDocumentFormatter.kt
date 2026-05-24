package dev.engine2d.nova

internal object NovaDocumentFormatter {
  fun format(source: String): String {
    if (source.isEmpty()) return source

    val lineSeparator = if (source.contains("\r\n")) "\r\n" else "\n"
    val result = StringBuilder(source.length)
    var index = 0

    while (index < source.length) {
      if (source.startsWith("<!--", index)) {
        val end = source.indexOf("-->", index + 4).let { if (it == -1) source.length else it + 3 }
        result.append(source, index, end)
        index = end
        continue
      }

      if (source[index] != '<') {
        result.append(source[index])
        index += 1
        continue
      }

      val tagEnd = findTagEnd(source, index)
      if (tagEnd == -1) {
        result.append(source.substring(index))
        break
      }

      val rawTag = source.substring(index, tagEnd + 1)
      val formattedTag = formatTag(rawTag, leadingIndent(source, index), lineSeparator)
      result.append(formattedTag)

      val tagName = tagName(rawTag)
      val isOpeningScriptLikeTag = tagName != null &&
        !rawTag.startsWith("</") &&
        !rawTag.trimEnd().endsWith("/>") &&
        tagName.lowercase() in RAW_TEXT_TAGS

      if (isOpeningScriptLikeTag) {
        val closingStart = indexOfClosingTag(source, tagName, tagEnd + 1)
        if (closingStart != -1) {
          result.append(source, tagEnd + 1, closingStart)
          index = closingStart
          continue
        }
      }

      index = tagEnd + 1
    }

    return result.toString()
  }

  private fun formatTag(rawTag: String, indent: String, lineSeparator: String): String {
    if (rawTag.startsWith("</") || rawTag.startsWith("<!") || rawTag.startsWith("<?")) return rawTag

    val parsed = parseTag(rawTag) ?: return rawTag
    if (parsed.attributes.isEmpty()) return rawTag

    val shouldWrap = rawTag.contains('\n') ||
      rawTag.contains('\r') ||
      parsed.attributes.size >= WRAP_ATTRIBUTE_COUNT ||
      rawTag.length > WRAP_LINE_LENGTH

    if (!shouldWrap) return rawTag

    return buildString {
      append('<').append(parsed.name).append(lineSeparator)
      for (attribute in parsed.attributes) {
        append(indent).append(ATTRIBUTE_INDENT).append(attribute).append(lineSeparator)
      }
      append(indent).append(if (parsed.selfClosing) "/>" else ">")
    }
  }

  private fun parseTag(rawTag: String): ParsedTag? {
    if (rawTag.length < 3 || rawTag.first() != '<' || rawTag.last() != '>') return null

    val content = rawTag.substring(1, rawTag.length - 1).trim()
    if (content.isEmpty() || content.startsWith('/')) return null

    val selfClosing = content.endsWith("/")
    val body = if (selfClosing) content.dropLast(1).trimEnd() else content
    val nameEnd = body.indexOfFirst { it.isWhitespace() }.let { if (it == -1) body.length else it }
    val name = body.substring(0, nameEnd)
    if (name.isBlank()) return null

    val attributesSource = body.substring(nameEnd).trim()
    val attributes = parseAttributes(attributesSource)

    return ParsedTag(name = name, attributes = attributes, selfClosing = selfClosing)
  }

  private fun parseAttributes(source: String): List<String> {
    if (source.isBlank()) return emptyList()

    val attributes = mutableListOf<String>()
    var index = 0
    while (index < source.length) {
      while (index < source.length && source[index].isWhitespace()) index += 1
      if (index >= source.length) break

      val start = index
      var quote = 0.toChar()
      var escaped = false

      while (index < source.length) {
        val current = source[index]
        if (quote != 0.toChar()) {
          if (escaped) {
            escaped = false
          } else if (current == '\\') {
            escaped = true
          } else if (current == quote) {
            quote = 0.toChar()
          }
          index += 1
          continue
        }

        if (current == '"' || current == '\'' || current == '`') {
          quote = current
          index += 1
          continue
        }

        if (current.isWhitespace()) break
        index += 1
      }

      source.substring(start, index).trim().takeIf { it.isNotEmpty() }?.let { attributes.add(it) }
    }

    return attributes
  }

  private fun findTagEnd(source: String, start: Int): Int {
    var index = start + 1
    var quote = 0.toChar()
    var escaped = false

    while (index < source.length) {
      val current = source[index]
      if (quote != 0.toChar()) {
        if (escaped) {
          escaped = false
        } else if (current == '\\') {
          escaped = true
        } else if (current == quote) {
          quote = 0.toChar()
        }
      } else if (current == '"' || current == '\'' || current == '`') {
        quote = current
      } else if (current == '>') {
        return index
      }
      index += 1
    }

    return -1
  }

  private fun leadingIndent(source: String, offset: Int): String {
    val lineStart = source.lastIndexOf('\n', offset - 1).let { if (it == -1) 0 else it + 1 }
    var index = lineStart
    while (index < offset && (source[index] == ' ' || source[index] == '\t')) index += 1
    return source.substring(lineStart, index)
  }

  private fun tagName(rawTag: String): String? {
    val start = if (rawTag.startsWith("</")) 2 else 1
    var index = start
    while (index < rawTag.length && (rawTag[index].isLetterOrDigit() || rawTag[index] == '-' || rawTag[index] == '.')) {
      index += 1
    }
    if (index == start) return null
    return rawTag.substring(start, index)
  }

  private fun indexOfClosingTag(source: String, tagName: String, start: Int): Int {
    val needle = "</${tagName.lowercase()}"
    val lowerSource = source.lowercase()
    return lowerSource.indexOf(needle, start)
  }

  private data class ParsedTag(
    val name: String,
    val attributes: List<String>,
    val selfClosing: Boolean,
  )

  private const val WRAP_ATTRIBUTE_COUNT = 4
  private const val WRAP_LINE_LENGTH = 100
  private const val ATTRIBUTE_INDENT = "  "

  private val RAW_TEXT_TAGS = setOf("script", "style")
}
