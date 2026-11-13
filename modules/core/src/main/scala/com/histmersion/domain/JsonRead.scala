package com.histmersion
package domain

import com.histmersion.Period

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.constraint.any.Pure

/** Shared reading helpers for the collection APIs. Pure. */
private[domain] object JsonRead:
  type Encoded = Encoded.T
  object Encoded extends Newtype[String, Pure]
  type Unencoded = Unencoded.T
  object Unencoded extends Newtype[String, Pure]

  def enc(s: Unencoded): Encoded = Encoded(URLEncoder.encode(s.value, UTF_8))
  def range(a: Option[DeepTime.SignedYear], b: Option[DeepTime.SignedYear]): Option[Period] =
    (a, b) match
      case (Some(x), Some(y)) => DeepTime.span(x, y)
      case (Some(x), None)    => DeepTime.span(x, x)
      case (None, Some(y))    => DeepTime.span(y, y)
      case _                  => None
