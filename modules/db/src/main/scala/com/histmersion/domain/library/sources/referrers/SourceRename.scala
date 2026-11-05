package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.library.Rename

/** A source given a new citekey: `from` becomes `to`; `shortForms` are the short citekeys (`beckman2025` for
  * `beckman2025_ahhiyawa`) that resolved to it before the rename.
  */
final case class SourceRename(from: SourceRename.From, to: SourceRename.To, shortForms: Set[SourceRename.ShortForms]):

  /** Where a link written as `target` points now, when it named the source. */
  def target(link: com.histmersion.domain.library.sources.referrers.SourceRename.TargetLink): Option[com.histmersion.domain.library.sources.referrers.SourceRename.TargetResult] = Option.when(link == from || shortForms(link))(to)

  /** A prefix of the old citekey and of every short form: a text without it links none of them. */
  def needle: com.histmersion.domain.library.sources.referrers.SourceRename.NeedleResult = from.takeWhile(_ != '_')

  /** The prefix of the source's finding ids under the old citekey (`<from>#`). */
  def findingPrefix: com.histmersion.domain.library.sources.referrers.SourceRename.FindingPrefixResult = from + "#"

  /** `text` with the old citekey replaced wherever it stands as a whole token. */
  def inText(text: com.histmersion.domain.library.sources.referrers.SourceRename.InTextText): com.histmersion.domain.library.sources.referrers.SourceRename.InTextResult = Rename.replaceToken(text, from, to)

object SourceRename:
  type TargetResult = TargetResult.T
  object TargetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetLink = TargetLink.T
  object TargetLink extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeedleResult = NeedleResult.T
  object NeedleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingPrefixResult = FindingPrefixResult.T
  object FindingPrefixResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InTextResult = InTextResult.T
  object InTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InTextText = InTextText.T
  object InTextText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type From = From.T
  object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type To = To.T
  object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShortForms = ShortForms.T
  object ShortForms extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
