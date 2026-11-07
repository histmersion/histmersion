package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.library.Rename

/** A source given a new citekey: `from` becomes `to`; `shortForms` are the short citekeys (`beckman2025` for
  * `beckman2025_ahhiyawa`) that resolved to it before the rename.
  */
final case class SourceRename(from: com.histmersion.domain.library.Citekey, to: com.histmersion.domain.library.Citekey, shortForms: Set[com.histmersion.domain.library.Citekey]):

  /** Where a link written as `target` points now, when it named the source. */
  def target(link: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.Citekey] = Option.when(link == from || shortForms(link))(to)

  /** A prefix of the old citekey and of every short form: a text without it links none of them. */
  def needle: com.histmersion.domain.library.sources.referrers.SourceRename.Needle = SourceRename.Needle(from.value.takeWhile(_ != '_'))

  /** The prefix of the source's finding ids under the old citekey (`<from>#`). */
  def findingPrefix: com.histmersion.domain.library.sources.referrers.SourceKeys.FindingPrefix = SourceKeys.FindingPrefix(from.value + "#")

  /** `text` with the old citekey replaced wherever it stands as a whole token. */
  def inText(text: com.histmersion.domain.library.sources.referrers.SourceKeys.Text): com.histmersion.domain.library.sources.referrers.SourceKeys.Text = SourceKeys.Text(Rename.replaceToken(text.value, com.histmersion.api.library.Citekey(from.value), com.histmersion.api.library.Citekey(to.value)))

object SourceRename:
  type Needle = Needle.T
  object Needle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
