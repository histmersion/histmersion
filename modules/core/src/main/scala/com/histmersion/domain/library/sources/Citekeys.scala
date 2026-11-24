package com.histmersion
package domain.library.sources
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw}

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.text.WikiLinks
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Citekey shape for a rename. */
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object Citekeys:
  type ValidCk = ValidCk.T
  object ValidCk extends Newtype[String, Pure]
  type RenameToNewRaw = RenameToNewRaw.T
  object RenameToNewRaw extends Newtype[String, Pure]

  private val Shape = "^[a-z][a-z0-9_]*$".r
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def valid(ck: ValidCk): Boolean = Shape.matches(ck.value)

  /** A source's new citekey, or why it cannot be one: an invalid key, the key it has, or a valid key that is not
    * link-shaped (`[[key]]` would resolve to an article, not the source).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def renameTo(oldCk: Citekey, newRaw: RenameToNewRaw): Either[Problem, Citekey] =
    Citekey.parse(newRaw.value).flatMap { ck =>
      if ck.value == oldCk.value.trim then Left(Invalid.Broken.here(Invalid.Rule.CitekeyUnchanged(Raw.of(ck))))
      else if !WikiLinks.isCitekeyShaped(ck.value) then
        Left(
          Invalid.Broken.here(
            Invalid.Rule.CitekeyLinkShaped(Raw.of(ck))
          )
        )
      else Right(ck)
    }
