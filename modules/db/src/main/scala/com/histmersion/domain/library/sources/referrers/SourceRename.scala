package com.histmersion
package domain.library.sources.referrers

import com.histmersion.api.library.Rename
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A source given a new citekey: `from` becomes `to`; `shortForms` are the short citekeys (`beckman2025` for
  * `beckman2025_ahhiyawa`) that resolved to it before the rename.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class SourceRename(from: Citekey, to: Citekey, shortForms: Set[Citekey]):

  /** Where a link written as `target` points now, when it named the source. */
  def target(link: Citekey): Option[Citekey] = Option.when(link == from || shortForms(link))(to)

  /** A prefix of the old citekey and of every short form: a text without it links none of them. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def needle: SourceRename.Needle = SourceRename.Needle(from.value.takeWhile(_ != '_'))

  /** The prefix of the source's finding ids under the old citekey (`<from>#`). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def findingPrefix: SourceKeys.FindingPrefix = SourceKeys.FindingPrefix(from.value + "#")

  /** `text` with the old citekey replaced wherever it stands as a whole token. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def inText(text: SourceKeys.Text): SourceKeys.Text = SourceKeys.Text(
    Rename.replaceToken(
      text.value,
      com.histmersion.api.library.Citekey(from.value),
      com.histmersion.api.library.Citekey(to.value)
    )
  )

object SourceRename:
  type Needle = Needle.T
  object Needle extends Newtype[String, Pure]
