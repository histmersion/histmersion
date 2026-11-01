package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.Rename
import com.histmersion.util.text.WikiLinks

/** A source a delete would touch: its citekey, and every key it answers to (the citekey and its old ones). `resolve`
  * gives the citekey each wikilink target names now, as the library resolves `[[x]]` (an unambiguous short form
  * included).
  */
final case class SourceKeys(citekey: String, keys: List[String], resolve: List[String] => Map[String, String]):

  /** The prefix of its finding ids (`<citekey>#`). */
  def findingPrefix: String = citekey + "#"

  /** Whether `text` holds the citekey as a whole token. */
  def tokenIn(text: String): Boolean = Rename.replaceToken(text, citekey, "\u0000") != text

  /** Whether `text` links the source under any of its keys. */
  def linkedIn(text: String): Boolean = keys.exists(k => WikiLinks.links(text, k))

  /** Whether one of the texts links the source as the library resolves its links. */
  def citedIn(texts: List[String]): Boolean =
    resolve(texts.flatMap(WikiLinks.targets).distinct).values.exists(_ == citekey)
