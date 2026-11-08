package com.histmersion
package domain

import com.histmersion.Period

import io.circe.ACursor

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Shared reading helpers for the collection APIs. Pure. */
private[domain] object JsonRead:
  type Encoded = Encoded.T
  object Encoded extends RefinedType[String, Pure]
  type Key = Key.T
  object Key extends RefinedType[String, Pure]
  type Number = Number.T
  object Number extends RefinedType[Long, Pure]
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  type Unencoded = Unencoded.T
  object Unencoded extends RefinedType[String, Pure]

  def str(c: ACursor, k: Key): Option[Text] =
    c.downField(k.value).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty).map(Text(_))
  def strs(c: ACursor, k: Key): List[Text] =
    c.downField(k.value)
      .focus
      .toList
      .flatMap(j => j.asArray.map(_.toList.flatMap(_.asString)).getOrElse(j.asString.toList))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Text(_))
  def long(c: ACursor, k: Key): Option[Number] =
    c.downField(k.value).focus.flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.trim.toLongOption))).map(Number(_))
  def bool(c: ACursor, k: Key): Option[Boolean] =
    c.downField(k.value).focus.flatMap(j => j.asBoolean.orElse(j.asString.map(_.trim.equalsIgnoreCase("true"))))
  def enc(s: Unencoded): Encoded = Encoded(URLEncoder.encode(s.value, UTF_8))
  def range(a: Option[DeepTime.SignedYear], b: Option[DeepTime.SignedYear]): Option[Period] =
    (a, b) match
      case (Some(x), Some(y)) => DeepTime.span(x, y)
      case (Some(x), None)    => DeepTime.span(x, x)
      case (None, Some(y))    => DeepTime.span(y, y)
      case _                  => None
