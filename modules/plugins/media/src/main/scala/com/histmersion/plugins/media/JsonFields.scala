package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor

import com.histmersion.plugin.{Period, Year}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.lint.{BoundaryKind, atBoundary}

/** A field as text: a string, or a number written out; blank is none. */
extension (c: Cursor)
  @atBoundary(BoundaryKind.Json, "reads a JSON field as text or a list of texts")
  def str(k: String): Option[String] = c.downField(k).literal

  /** A field's strings: an array of them, or one; blanks dropped. */
  def strs(k: String): List[String] = c.downField(k).strings

  def long(k: String): Option[Long] = c.downField(k).integer

  def bool(k: String): Option[Boolean] = c.downField(k).boolean

/** A query parameter's value, URL-encoded. */
extension (s: String) def enc: String = URLEncoder.encode(s, UTF_8)

extension (p: Period.type)
  /** The span of two signed years in either order; none when one is not a year. */
  def span(a: Long, b: Long): Option[Period] =
    Option
      .when(a.isValidInt && b.isValidInt)(Period.from(Year(a.min(b).toInt), Year(a.max(b).toInt)).toOption)
      .flatten

  /** The span two optional years give: both, or the one there is; none without either. */
  def range(a: Option[Long], b: Option[Long]): Option[Period] =
    (a, b) match
      case (Some(x), Some(y)) => Period.span(x, y)
      case (Some(x), None)    => Period.span(x, x)
      case (None, Some(y))    => Period.span(y, y)
      case _                  => None
