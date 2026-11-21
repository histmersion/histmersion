package com.histmersion
package server
package comments

final class CommentLexer(text: String, language: SourceLanguage):
  private val size = text.length
  private val found = Vector.newBuilder[SourceComment]

  private val lineStarts: Array[Int] =
    (0 +: text.indices.filter(i => text.charAt(i) == '\n').map(_ + 1)).toArray

  lazy val comments: Vector[SourceComment] =
    language match
      case SourceLanguage.Scala | SourceLanguage.Java => val _ = cFamily(0, size, braces = false)
      case SourceLanguage.JavaScript                  => val _ = script(0, size, braces = false)
      case SourceLanguage.Css                         => css(0, size)
      case SourceLanguage.Sql                         => sql()
      case SourceLanguage.Python                      => val _ = python(0, braces = false)
      case SourceLanguage.Shell                       => shell()
      case SourceLanguage.Html                        => html()
    found.result().sortBy(_.start)

  private def at(i: Int, s: String): Boolean = text.startsWith(s, i)

  private def charAt(i: Int): Char = if i >= 0 && i < size then text.charAt(i) else '\u0000'

  private def lineOf(offset: Int): Int =
    val found = java.util.Arrays.binarySearch(lineStarts, offset)
    if found >= 0 then found + 1 else -found - 1

  private def record(start: Int, end: Int): Unit =
    found += SourceComment(start, end, lineOf(start), lineOf(math.max(start, end - 1)), text.substring(start, end))

  private def lineEnd(i: Int): Int =
    val n = text.indexOf('\n', i)
    if n < 0 then size else n

  private def identifierChar(c: Char): Boolean = c.isLetterOrDigit || c == '_'

  private def lineComment(i: Int, until: Int): Int =
    val end = math.min(lineEnd(i), until)
    record(i, end)
    end

  private def blockComment(i: Int, nested: Boolean): Int =
    var j = i + 2
    var depth = 1
    while j < size && depth > 0 do
      if at(j, "*/") then
        depth -= 1
        j += 2
      else if nested && at(j, "/*") then
        depth += 1
        j += 2
      else j += 1
    record(i, j)
    j

  private def cFamily(from: Int, until: Int, braces: Boolean): Int =
    var i = from
    var depth = 0
    var closed = -1
    while i < until && closed < 0 do
      val c = text.charAt(i)
      if at(i, "//") then i = lineComment(i, until)
      else if at(i, "/*") then i = blockComment(i, nested = language == SourceLanguage.Scala)
      else if c == '"' then i = scalaString(i)
      else if c == '\'' then i = charLiteral(i)
      else if c == '`' && language == SourceLanguage.Scala then i = backtick(i)
      else if braces && c == '{' then
        depth += 1
        i += 1
      else if braces && c == '}' then
        if depth == 0 then closed = i + 1 else depth -= 1
        i += 1
      else i += 1
    if closed < 0 then i else closed

  private def scalaString(i: Int): Int =
    val interpolated = language == SourceLanguage.Scala && i > 0 && identifierChar(text.charAt(i - 1))
    val triple = at(i, "\"\"\"")
    var j = if triple then i + 3 else i + 1
    var end = -1
    while j < size && end < 0 do
      val c = text.charAt(j)
      if !triple && c == '\\' then j += 2
      else if interpolated && at(j, "$$") then j += 2
      else if interpolated && at(j, "${") then j = cFamily(j + 2, size, braces = true)
      else if triple && at(j, "\"\"\"") then
        var k = j + 3
        while k < size && text.charAt(k) == '"' do k += 1
        end = k
      else if !triple && c == '"' then end = j + 1
      else if !triple && c == '\n' then end = j
      else j += 1
    if end < 0 then size else end

  private def charLiteral(i: Int): Int =
    if charAt(i + 1) == '\\' then
      var j = i + 3
      while j < size && text.charAt(j) != '\'' && text.charAt(j) != '\n' do j += 1
      if charAt(j) == '\'' then j + 1 else i + 1
    else if charAt(i + 2) == '\'' && charAt(i + 1) != '\n' then i + 3
    else if Character.isHighSurrogate(charAt(i + 1)) && charAt(i + 3) == '\'' then i + 4
    else i + 1

  private def backtick(i: Int): Int =
    val close = text.indexOf('`', i + 1)
    if close < 0 || close > lineEnd(i) then i + 1 else close + 1

  private def quoted(i: Int, quote: Char): Int =
    var j = i + 1
    var end = -1
    while j < size && end < 0 do
      val c = text.charAt(j)
      if c == '\\' then j += 2
      else if c == quote then end = j + 1
      else if c == '\n' then end = j
      else j += 1
    if end < 0 then size else end

  private def script(from: Int, until: Int, braces: Boolean): Int =
    var i = from
    var depth = 0
    var closed = -1
    while i < until && closed < 0 do
      val c = text.charAt(i)
      if at(i, "//") then i = lineComment(i, until)
      else if at(i, "/*") then i = blockComment(i, nested = false)
      else if c == '\'' || c == '"' then i = quoted(i, c)
      else if c == '`' then i = template(i)
      else if c == '/' && regexAllowed(i) then i = regex(i)
      else if braces && c == '{' then
        depth += 1
        i += 1
      else if braces && c == '}' then
        if depth == 0 then closed = i + 1 else depth -= 1
        i += 1
      else i += 1
    if closed < 0 then i else closed

  private def template(i: Int): Int =
    var j = i + 1
    var end = -1
    while j < size && end < 0 do
      if text.charAt(j) == '\\' then j += 2
      else if at(j, "${") then j = script(j + 2, size, braces = true)
      else if text.charAt(j) == '`' then end = j + 1
      else j += 1
    if end < 0 then size else end

  private def regexAllowed(i: Int): Boolean =
    var p = i - 1
    while p >= 0 && text.charAt(p).isWhitespace do p -= 1
    if p < 0 then true
    else
      val c = text.charAt(p)
      if "(,=:[!&|?{};+-*%<>~^".contains(c) then true
      else if identifierChar(c) then
        var s = p
        while s > 0 && identifierChar(text.charAt(s - 1)) do s -= 1
        CommentLexer.keywordsBeforeRegex.contains(text.substring(s, p + 1))
      else false

  private def regex(i: Int): Int =
    var j = i + 1
    var inClass = false
    var end = -1
    while j < size && end < 0 do
      val c = text.charAt(j)
      if c == '\\' then j += 2
      else if c == '\n' then end = -2
      else if c == '[' then
        inClass = true
        j += 1
      else if c == ']' then
        inClass = false
        j += 1
      else if c == '/' && !inClass then end = j + 1
      else j += 1
    if end == -2 || end < 0 then i + 1
    else
      var k = end
      while k < size && text.charAt(k).isLetter do k += 1
      k

  private def css(from: Int, until: Int): Unit =
    var i = from
    while i < until do
      val c = text.charAt(i)
      if at(i, "/*") then i = blockComment(i, nested = false)
      else if c == '\'' || c == '"' then i = quoted(i, c)
      else i += 1

  private def sql(): Unit =
    var i = 0
    while i < size do
      val c = text.charAt(i)
      if at(i, "--") then i = lineComment(i, size)
      else if at(i, "/*") then i = blockComment(i, nested = true)
      else if c == '\'' then i = sqlQuoted(i, '\'', escapes = charAt(i - 1) == 'E' || charAt(i - 1) == 'e')
      else if c == '"' then i = sqlQuoted(i, '"', escapes = false)
      else if c == '$' && !identifierChar(charAt(i - 1)) then i = dollarQuoted(i)
      else i += 1

  private def sqlQuoted(i: Int, quote: Char, escapes: Boolean): Int =
    var j = i + 1
    var end = -1
    while j < size && end < 0 do
      val c = text.charAt(j)
      if escapes && c == '\\' then j += 2
      else if c == quote then end = j + 1
      else j += 1
    if end < 0 then size else end

  private def dollarQuoted(i: Int): Int =
    var j = i + 1
    while j < size && (text.charAt(j).isLetter || text.charAt(j) == '_') do j += 1
    if charAt(j) != '$' then i + 1
    else
      val tag = text.substring(i, j + 1)
      val close = text.indexOf(tag, j + 1)
      if close < 0 then size else close + tag.length

  private def python(from: Int, braces: Boolean): Int =
    var i = from
    var depth = 0
    var closed = -1
    while i < size && closed < 0 do
      val c = text.charAt(i)
      if c == '#' then i = lineComment(i, size)
      else if c == '\'' || c == '"' then i = pythonString(i, c)
      else if braces && c == '{' then
        depth += 1
        i += 1
      else if braces && c == '}' then
        if depth == 0 then closed = i + 1 else depth -= 1
        i += 1
      else i += 1
    if closed < 0 then i else closed

  private def pythonString(i: Int, quote: Char): Int =
    var p = i
    while p > 0 && i - p < 2 && "rRbBfFuU".contains(text.charAt(p - 1)) do p -= 1
    val s = if identifierChar(charAt(p - 1)) then i else p
    val formatted = text.substring(s, i).exists(c => c == 'f' || c == 'F')
    val triple = at(i, s"$quote$quote$quote")
    var j = if triple then i + 3 else i + 1
    var end = -1
    while j < size && end < 0 do
      val c = text.charAt(j)
      if c == '\\' then j += 2
      else if formatted && (at(j, "{{") || at(j, "}}")) then j += 2
      else if formatted && c == '{' then j = python(j + 1, braces = true)
      else if triple && at(j, s"$quote$quote$quote") then end = j + 3
      else if !triple && c == quote then end = j + 1
      else if !triple && c == '\n' then end = j
      else j += 1
    val stop = if end < 0 then size else end
    if docstring(s, stop) then record(s, stop)
    stop

  private def indentOf(line: String): Int = line.segmentLength(c => c == ' ' || c == '\t')

  private def docstring(start: Int, end: Int): Boolean =
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    val before = text.substring(lineStart, start)
    val after = text.substring(end, lineEnd(end)).trim
    if !before.isBlank || !(after.isEmpty || after.startsWith("#")) then false
    else
      val previous = text
        .substring(0, lineStart)
        .linesIterator
        .toVector
        .filterNot(l => l.isBlank || l.trim.startsWith("#"))
      previous.lastOption match
        case None                                   => true
        case Some(last) if !last.trim.endsWith(":") => false
        case Some(_)                                =>
          val indent = before.length
          previous.reverseIterator.find(l => indentOf(l) < indent).exists { opener =>
            val t = opener.trim
            t.startsWith("def ") || t.startsWith("async def ") || t.startsWith("class ")
          }

  private def shell(): Unit =
    var i = 0
    var heredocs = Vector.empty[(String, Boolean)]
    while i < size do
      val c = text.charAt(i)
      if c == '\n' && heredocs.nonEmpty then
        i = skipHeredocs(i + 1, heredocs)
        heredocs = Vector.empty
      else if c == '\\' then i += 2
      else if c == '\'' then
        val close = text.indexOf('\'', i + 1)
        i = if close < 0 then size else close + 1
      else if c == '"' then i = quoted(i, '"')
      else if c == '#' && (i == 0 || text.charAt(i - 1).isWhitespace || ";|&()".contains(text.charAt(i - 1))) then
        i = lineComment(i, size)
      else if at(i, "<<") && !at(i, "<<<") then
        var k = i + 2
        val strip = charAt(k) == '-'
        if strip then k += 1
        while charAt(k) == ' ' || charAt(k) == '\t' do k += 1
        val quote = charAt(k)
        if quote == '\'' || quote == '"' then k += 1
        val wordStart = k
        while identifierChar(charAt(k)) do k += 1
        if k > wordStart then heredocs = heredocs :+ (text.substring(wordStart, k) -> strip)
        if (quote == '\'' || quote == '"') && charAt(k) == quote then k += 1
        i = math.max(k, i + 2)
      else i += 1

  private def skipHeredocs(from: Int, terminators: Vector[(String, Boolean)]): Int =
    var pos = from
    terminators.foreach { (word, strip) =>
      var done = false
      while pos < size && !done do
        val end = lineEnd(pos)
        val line = text.substring(pos, end)
        val compared = if strip then line.dropWhile(_ == '\t') else line
        done = compared == word
        pos = end + 1
    }
    math.min(pos, size)

  private def html(): Unit =
    var i = 0
    val lower = text.toLowerCase
    while i < size do
      if at(i, "<!--") then
        val close = text.indexOf("-->", i + 4)
        val end = if close < 0 then size else close + 3
        record(i, end)
        i = end
      else if lower.startsWith("<script", i) || lower.startsWith("<style", i) then
        val isScript = lower.startsWith("<script", i)
        val open = text.indexOf('>', i)
        val closeTag = if isScript then "</script" else "</style"
        val close = if open < 0 then -1 else lower.indexOf(closeTag, open)
        val until = if close < 0 then size else close
        if open >= 0 then
          if isScript then
            val _ = script(open + 1, until, braces = false)
          else css(open + 1, until)
        i = math.max(until, i + 1)
      else i += 1

object CommentLexer:
  val keywordsBeforeRegex: Set[String] =
    Set("return", "typeof", "case", "in", "of", "delete", "void", "throw", "new", "yield", "await", "else", "do")
