package com.histmersion.domain

import com.histmersion.Period

import io.circe.ACursor

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/** Shared reading helpers for the collection APIs. Pure. */
private[domain] object JsonRead:
  def str(c: ACursor, k: String): Option[String] =
    c.downField(k).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)
  def strs(c: ACursor, k: String): List[String] =
    c.downField(k)
      .focus
      .toList
      .flatMap(j => j.asArray.map(_.toList.flatMap(_.asString)).getOrElse(j.asString.toList))
      .map(_.trim)
      .filter(_.nonEmpty)
  def long(c: ACursor, k: String): Option[Long] =
    c.downField(k).focus.flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.trim.toLongOption)))
  def bool(c: ACursor, k: String): Option[Boolean] =
    c.downField(k).focus.flatMap(j => j.asBoolean.orElse(j.asString.map(_.trim.equalsIgnoreCase("true"))))
  def enc(s: String): String = URLEncoder.encode(s, UTF_8)
  def range(a: Option[Long], b: Option[Long]): Option[Period] =
    (a, b) match
      case (Some(x), Some(y)) => DeepTime.span(x, y)
      case (Some(x), None)    => DeepTime.span(x, x)
      case (None, Some(y))    => DeepTime.span(y, y)
      case _                  => None
