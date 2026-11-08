package com.histmersion
package domain.library.sources.referrers

import com.histmersion.api.library.Rename
import com.histmersion.api.text.WikiLinks
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

/** A source a delete would touch: its citekey, and every key it answers to (the citekey and its old ones). `resolve`
  * gives the citekey each wikilink target names now, as the library resolves `[[x]]` (an unambiguous short form
  * included).
  */
final case class SourceKeys(citekey: Citekey, keys: List[Citekey], resolve: List[WikiLinks.Occurrence.Target] => Map[WikiLinks.Occurrence.Target, Citekey]):

  /** The prefix of its finding ids (`<citekey>#`). */
  def findingPrefix: SourceKeys.FindingPrefix = SourceKeys.FindingPrefix(citekey.value + "#")

  /** Whether `text` holds the citekey as a whole token. */
  def tokenIn(text: SourceKeys.Text): Boolean = Rename.replaceToken(text.value, com.histmersion.api.library.Citekey(citekey.value), com.histmersion.api.library.Citekey("\u0000")) != text.value

  /** Whether `text` links the source under any of its keys. */
  def linkedIn(text: SourceKeys.Text): Boolean = keys.exists(k => WikiLinks.links(WikiLinks.Body(text.value), com.histmersion.api.library.Citekey(k.value)))

  /** Whether one of the texts links the source as the library resolves its links. */
  def citedIn(texts: List[SourceKeys.Text]): Boolean =
    resolve(texts.flatMap(t => WikiLinks.targets(WikiLinks.Body(t.value))).distinct).values.exists(_ == citekey)

object SourceKeys:
  type FindingPrefix = FindingPrefix.T
  object FindingPrefix extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]


  type Keys = Citekey
  val Keys: Citekey.type = Citekey
