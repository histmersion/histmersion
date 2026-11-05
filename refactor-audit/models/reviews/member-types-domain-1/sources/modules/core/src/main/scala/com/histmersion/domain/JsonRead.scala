package com.histmersion.domain

import com.histmersion.Period

import io.circe.ACursor

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/** Shared reading helpers for the collection APIs. Pure. */
private[domain] object JsonRead:
  type StrResult = StrResult.T
  object StrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrK = StrK.T
  object StrK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrsResult = StrsResult.T
  object StrsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrsK = StrsK.T
  object StrsK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LongResult = LongResult.T
  object LongResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LongK = LongK.T
  object LongK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoolResult = BoolResult.T
  object BoolResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoolK = BoolK.T
  object BoolK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncResult = EncResult.T
  object EncResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncS = EncS.T
  object EncS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeA = RangeA.T
  object RangeA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeB = RangeB.T
  object RangeB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def str(c: ACursor, k: com.histmersion.domain.JsonRead.StrK): Option[com.histmersion.domain.JsonRead.StrResult] =
    c.downField(k).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)
  def strs(c: ACursor, k: com.histmersion.domain.JsonRead.StrsK): List[com.histmersion.domain.JsonRead.StrsResult] =
    c.downField(k)
      .focus
      .toList
      .flatMap(j => j.asArray.map(_.toList.flatMap(_.asString)).getOrElse(j.asString.toList))
      .map(_.trim)
      .filter(_.nonEmpty)
  def long(c: ACursor, k: com.histmersion.domain.JsonRead.LongK): Option[com.histmersion.domain.JsonRead.LongResult] =
    c.downField(k).focus.flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.trim.toLongOption)))
  def bool(c: ACursor, k: com.histmersion.domain.JsonRead.BoolK): Option[com.histmersion.domain.JsonRead.BoolResult] =
    c.downField(k).focus.flatMap(j => j.asBoolean.orElse(j.asString.map(_.trim.equalsIgnoreCase("true"))))
  def enc(s: com.histmersion.domain.JsonRead.EncS): com.histmersion.domain.JsonRead.EncResult = URLEncoder.encode(s, UTF_8)
  def range(a: Option[com.histmersion.domain.JsonRead.RangeA], b: Option[com.histmersion.domain.JsonRead.RangeB]): Option[Period] =
    (a, b) match
      case (Some(x), Some(y)) => DeepTime.span(x, y)
      case (Some(x), None)    => DeepTime.span(x, x)
      case (None, Some(y))    => DeepTime.span(y, y)
      case _                  => None
