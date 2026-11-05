package com.histmersion.domain.library.sources.referrers

import com.histmersion.api.library.Rename
import com.histmersion.api.text.WikiLinks

/** A source a delete would touch: its citekey, and every key it answers to (the citekey and its old ones). `resolve`
  * gives the citekey each wikilink target names now, as the library resolves `[[x]]` (an unambiguous short form
  * included).
  */
final case class SourceKeys(citekey: com.histmersion.domain.library.Citekey, keys: List[SourceKeys.Keys], resolve: List[SourceKeys.ResolveArg1] => Map[SourceKeys.ResolveResultKey, SourceKeys.ResolveResultValue]):

  /** The prefix of its finding ids (`<citekey>#`). */
  def findingPrefix: com.histmersion.domain.library.sources.referrers.SourceKeys.FindingPrefixResult = citekey + "#"

  /** Whether `text` holds the citekey as a whole token. */
  def tokenIn(text: com.histmersion.domain.library.sources.referrers.SourceKeys.TokenInText): com.histmersion.domain.library.sources.referrers.SourceKeys.TokenInResult = Rename.replaceToken(text, citekey, "\u0000") != text

  /** Whether `text` links the source under any of its keys. */
  def linkedIn(text: com.histmersion.domain.library.sources.referrers.SourceKeys.LinkedInText): com.histmersion.domain.library.sources.referrers.SourceKeys.LinkedInResult = keys.exists(k => WikiLinks.links(text, k))

  /** Whether one of the texts links the source as the library resolves its links. */
  def citedIn(texts: List[com.histmersion.domain.library.sources.referrers.SourceKeys.CitedInTexts]): com.histmersion.domain.library.sources.referrers.SourceKeys.CitedInResult =
    resolve(texts.flatMap(WikiLinks.targets).distinct).values.exists(_ == citekey)

object SourceKeys:
  type ResolveArg1 = ResolveArg1.T
  object ResolveArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultKey = ResolveResultKey.T
  object ResolveResultKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultValue = ResolveResultValue.T
  object ResolveResultValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FindingPrefixResult = FindingPrefixResult.T
  object FindingPrefixResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokenInResult = TokenInResult.T
  object TokenInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokenInText = TokenInText.T
  object TokenInText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedInResult = LinkedInResult.T
  object LinkedInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedInText = LinkedInText.T
  object LinkedInText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedInResult = CitedInResult.T
  object CitedInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedInTexts = CitedInTexts.T
  object CitedInTexts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Keys = Keys.T
  object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
