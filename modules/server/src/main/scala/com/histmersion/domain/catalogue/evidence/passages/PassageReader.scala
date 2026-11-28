package com.histmersion
package domain.catalogue.evidence.passages

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** One source's text, searched for passages: a phrase or a regular expression, each hit with its context; `limit`
  * passages at most.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
trait PassageReader:
  def find(
      query: PassageReader.Query,
      regex: PassageReader.Regex,
      limit: PageSize,
      context: PassageReader.Context,
      maxMatches: PassageReader.MaxMatches,
      timeoutMs: PassageReader.TimeoutMs
  ): Either[Passage.Text, List[Passage]]

object PassageReader:
  type Context = Context.T
  object Context extends Newtype[Int, Pure]
  type MaxMatches = MaxMatches.T
  object MaxMatches extends Newtype[Int, Pure]
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  enum Regex derives CanEqual:
    case Regex, Literal
  object Regex extends TwoCases[Regex]:
    extension (regex: Regex) def regex: Boolean = regex == Regex
  type TimeoutMs = TimeoutMs.T
  object TimeoutMs extends Newtype[Long, Pure]
