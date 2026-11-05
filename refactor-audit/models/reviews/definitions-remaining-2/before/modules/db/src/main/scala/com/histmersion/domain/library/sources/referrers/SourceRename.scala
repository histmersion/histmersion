package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.library.Rename

/** A source given a new citekey: `from` becomes `to`; `shortForms` are the short citekeys (`beckman2025` for
  * `beckman2025_ahhiyawa`) that resolved to it before the rename.
  */
final case class SourceRename(from: String, to: String, shortForms: Set[String]):

  /** Where a link written as `target` points now, when it named the source. */
  def target(link: String): Option[String] = Option.when(link == from || shortForms(link))(to)

  /** A prefix of the old citekey and of every short form: a text without it links none of them. */
  def needle: String = from.takeWhile(_ != '_')

  /** The prefix of the source's finding ids under the old citekey (`<from>#`). */
  def findingPrefix: String = from + "#"

  /** `text` with the old citekey replaced wherever it stands as a whole token. */
  def inText(text: String): String = Rename.replaceToken(text, from, to)
