package com.histmersion
package domain

import com.histmersion.Period

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Shared reading helpers for the collection APIs. Pure. */
@atBoundary(BoundaryKind.Json, "reads fields out of a stored JSON document for the collection APIs")
private[domain] object JsonRead:
  type Encoded = Encoded.T
  object Encoded extends Newtype[String, Pure]
  type Unencoded = Unencoded.T
  object Unencoded extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def enc(s: Unencoded): Encoded = Encoded(URLEncoder.encode(s.value, UTF_8))
  def range(a: Option[DeepTime.SignedYear], b: Option[DeepTime.SignedYear]): Option[Period] =
    (a, b) match
      case (Some(x), Some(y)) => DeepTime.span(x, y)
      case (Some(x), None)    => DeepTime.span(x, x)
      case (None, Some(y))    => DeepTime.span(y, y)
      case _                  => None
