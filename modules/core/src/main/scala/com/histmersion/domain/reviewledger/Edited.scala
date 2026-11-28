package com.histmersion
package domain.reviewledger

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.reviewledger.Excerpt
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.reviewarticle.Fix
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class Edited(passages: List[Excerpt], removed: List[Edited.Removed]) derives JsonCodec:
  def isEmpty: Boolean = passages.isEmpty && removed.isEmpty

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text: Fix.Body = Fix.Body(passages.map(_.text.value).mkString("\n\n"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def render: String =
    val ps = passages.map(p => p.heading.fold("")(h => s"[under: $h]\n") + p.text).mkString("\n\n---\n\n")
    val rs =
      if removed.isEmpty then ""
      else "\n\n---\n\nRemoved:\n" + removed.map(r => s"- ${r.value.take(300)}").mkString("\n")
    ps + rs

object Edited:

  type Removed = Removed.T
  object Removed extends Newtype[String, Pure]
