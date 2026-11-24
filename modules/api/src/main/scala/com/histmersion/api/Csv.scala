package com.histmersion
package api

import com.histmersion.lint.{BoundaryKind, FeaturePlace, atBoundary, movesTo}
import com.histmersion.lint.{LintRule, tolerated}
/** A minimal RFC 4180 reader (quoted fields, doubled quotes, embedded commas and newlines, CRLF): enough for the
  * Retraction Watch export.
  */
@atBoundary(BoundaryKind.Json, "reads CSV text (the Retraction Watch export) into rows")
@movesTo(FeaturePlace.UtilCommon)
object Csv:

  def parse(text: String): List[List[String]] =
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

    val s = (text).replace("\r\n", "\n").replace('\r', '\n')
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
    (rows.result()).map(refactorValue => (refactorValue).map(refactorValue2 => (refactorValue2)))
