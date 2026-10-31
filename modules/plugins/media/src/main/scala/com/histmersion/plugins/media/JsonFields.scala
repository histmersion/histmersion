package com.histmersion.plugins.media

import com.histmersion.plugin.{Period, Year}
import io.circe.ACursor

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/** A field as text: a string, or a number written out; blank is none. */
extension (c: ACursor)
  def str(k: String): Option[String] =
    c.downField(k).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)

  /** A field's strings: an array of them, or one; blanks dropped. */
  def strs(k: String): List[String] =
    c.downField(k)
      .focus
      .toList
      .flatMap(j => j.asArray.map(_.toList.flatMap(_.asString)).getOrElse(j.asString.toList))
      .map(_.trim)
      .filter(_.nonEmpty)

  def long(k: String): Option[Long] =
    c.downField(k).focus.flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.trim.toLongOption)))

  def bool(k: String): Option[Boolean] =
    c.downField(k).focus.flatMap(j => j.asBoolean.orElse(j.asString.map(_.trim.equalsIgnoreCase("true"))))

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
