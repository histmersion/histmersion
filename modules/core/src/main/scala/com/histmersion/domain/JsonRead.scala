package com.histmersion.domain

import com.histmersion.Period

import io.circe.ACursor

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/** Shared reading helpers for the collection APIs. Pure. */
private[domain] object JsonRead:
  type Encoded = Encoded.T
  object Encoded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Number = Number.T
  object Number extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeEnd = RangeEnd.T
  object RangeEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeStart = RangeStart.T
  object RangeStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unencoded = Unencoded.T
  object Unencoded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def str(c: ACursor, k: com.histmersion.domain.JsonRead.Key): Option[com.histmersion.domain.JsonRead.Text] =
    c.downField(k).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)
  def strs(c: ACursor, k: com.histmersion.domain.JsonRead.Key): List[com.histmersion.domain.JsonRead.Text] =
    c.downField(k)
      .focus
      .toList
      .flatMap(j => j.asArray.map(_.toList.flatMap(_.asString)).getOrElse(j.asString.toList))
      .map(_.trim)
      .filter(_.nonEmpty)
  def long(c: ACursor, k: com.histmersion.domain.JsonRead.Key): Option[com.histmersion.domain.JsonRead.Number] =
    c.downField(k).focus.flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.trim.toLongOption)))
  def bool(c: ACursor, k: com.histmersion.domain.JsonRead.Key): Option[Boolean] =
    c.downField(k).focus.flatMap(j => j.asBoolean.orElse(j.asString.map(_.trim.equalsIgnoreCase("true"))))
  def enc(s: com.histmersion.domain.JsonRead.Unencoded): com.histmersion.domain.JsonRead.Encoded = URLEncoder.encode(s, UTF_8)
  def range(a: Option[com.histmersion.domain.JsonRead.RangeStart], b: Option[com.histmersion.domain.JsonRead.RangeEnd]): Option[Period] =
    (a, b) match
      case (Some(x), Some(y)) => DeepTime.span(x, y)
      case (Some(x), None)    => DeepTime.span(x, x)
      case (None, Some(y))    => DeepTime.span(y, y)
      case _                  => None
