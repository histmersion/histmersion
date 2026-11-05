package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.library.Rename

/** A source given a new citekey: `from` becomes `to`; `shortForms` are the short citekeys (`beckman2025` for
  * `beckman2025_ahhiyawa`) that resolved to it before the rename.
  */
final case class SourceRename(from: SourceRename.From, to: SourceRename.To, shortForms: Set[SourceRename.ShortForms]):

  /** Where a link written as `target` points now, when it named the source. */
  def target(link: String): Option[String] = Option.when(link == from || shortForms(link))(to)

  /** A prefix of the old citekey and of every short form: a text without it links none of them. */
  def needle: String = from.takeWhile(_ != '_')

  /** The prefix of the source's finding ids under the old citekey (`<from>#`). */
  def findingPrefix: String = from + "#"

  /** `text` with the old citekey replaced wherever it stands as a whole token. */
  def inText(text: String): String = Rename.replaceToken(text, from, to)

object SourceRename:
  type From = From.T
  object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type To = To.T
  object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShortForms = ShortForms.T
  object ShortForms extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
