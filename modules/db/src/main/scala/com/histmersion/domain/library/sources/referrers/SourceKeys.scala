package com.histmersion
package domain.library.sources.referrers

import com.histmersion.api.library.Rename
import com.histmersion.api.text.WikiLinks
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A source a delete would touch: its citekey, and every key it answers to (the citekey and its old ones). `resolve`
  * gives the citekey each wikilink target names now, as the library resolves `[[x]]` (an unambiguous short form
  * included).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class SourceKeys(
    citekey: Citekey,
    keys: List[Citekey],
    resolve: List[WikiLinks.Occurrence.Target] => Map[WikiLinks.Occurrence.Target, Citekey]
):

  /** The prefix of its finding ids (`<citekey>#`). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def findingPrefix: SourceKeys.FindingPrefix = SourceKeys.FindingPrefix(citekey.value + "#")

  /** Whether `text` holds the citekey as a whole token. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def tokenIn(text: SourceKeys.Text): Boolean = Rename.replaceToken(
    text.value,
    com.histmersion.api.library.Citekey(citekey.value),
    com.histmersion.api.library.Citekey("\u0000")
  ) != text.value

  /** Whether `text` links the source under any of its keys. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def linkedIn(text: SourceKeys.Text): Boolean =
    keys.exists(k => WikiLinks.links(WikiLinks.Body(text.value), com.histmersion.api.library.Citekey(k.value)))

  /** Whether one of the texts links the source as the library resolves its links. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def citedIn(texts: List[SourceKeys.Text]): Boolean =
    resolve(texts.flatMap(t => WikiLinks.targets(WikiLinks.Body(t.value))).distinct).values.exists(_ == citekey)

object SourceKeys:
  type FindingPrefix = FindingPrefix.T
  object FindingPrefix extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Keys = Citekey
  val Keys: Citekey.type = Citekey
