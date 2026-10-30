package com.histmersion.api

/** A minimal RFC 4180 reader: quoted fields, doubled quotes inside them, embedded commas and newlines, and CRLF line
  * endings. Enough for the Retraction Watch export and nothing more.
  */
object Csv:

  def parse(text: String): List[List[String]] =
    val rows = List.newBuilder[List[String]]
    var row = List.newBuilder[String] // scanning a CSV is inherently a fold over characters with
    val field = new StringBuilder // two pieces of state; a var pair is the honest encoding
    var quoted = false
    var i = 0
    var any = false // did this row have any content at all (skip blank lines)

    def endField(): Unit = { row += field.toString; field.setLength(0) }
    def endRow(): Unit =
      endField()
      val r = row.result()
      if any then rows += r
      row = List.newBuilder[String]
      any = false

    val s = text.replace("\r\n", "\n").replace('\r', '\n')
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
    rows.result()
