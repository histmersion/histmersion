package com.histmersion.api

/** A minimal RFC 4180 reader (quoted fields, doubled quotes, embedded commas and newlines, CRLF): enough for the
  * Retraction Watch export.
  */
object Csv:

  type ParseResult = ParseResult.T
  object ParseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseText = ParseText.T
  object ParseText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(text: com.histmersion.api.Csv.ParseText): List[List[com.histmersion.api.Csv.ParseResult]] =
    val rows = List.newBuilder[List[String]]
    var row = List.newBuilder[String]
    val field = new StringBuilder
    var quoted = false
    var i = 0
    var any = false

    def endField(): Unit = { row += field.toString; field.setLength(0) }
    def endRow(): Unit =
      endField()
      val r = row.result()
      if any then rows += r
      row = List.newBuilder[String]
      any = false

    val s = (text).value.replace("\r\n", "\n").replace('\r', '\n')
    while i < s.length do
      val c = s.charAt(i)
      if quoted then
        if c == '"' then
          if i + 1 < s.length && s.charAt(i + 1) == '"' then { field += '"'; i += 1 }
          else quoted = false
        else field += c
        any = true
      else
        c match
          case '"'  => quoted = true; any = true
          case ','  => endField(); any = true
          case '\n' => endRow()
          case _    => field += c; if !c.isWhitespace then any = true
      i += 1
    endRow()
    (rows.result()).map(refactorValue => (refactorValue).map(refactorValue2 => _root_.com.histmersion.api.Csv.ParseResult(refactorValue2)))
