package com.histmersion.domain.catalogue.evidence.passages

/** One source's text, searched for passages: a phrase or a regular expression, each hit with its context; `limit`
  * passages at most.
  */
trait PassageReader:
  def find(
      query: com.histmersion.domain.catalogue.evidence.passages.PassageReader.Query,
      regex: com.histmersion.domain.catalogue.evidence.passages.PassageReader.Regex,
      limit: com.histmersion.PageSize,
      context: com.histmersion.domain.catalogue.evidence.passages.PassageReader.Context,
      maxMatches: com.histmersion.domain.catalogue.evidence.passages.PassageReader.MaxMatches,
      timeoutMs: com.histmersion.domain.catalogue.evidence.passages.PassageReader.TimeoutMs
  ): Either[com.histmersion.domain.catalogue.evidence.passages.Passage.Text, List[Passage]]

object PassageReader:
  type Context = Context.T
  object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxMatches = MaxMatches.T
  object MaxMatches extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regex = Regex.T
  object Regex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutMs = TimeoutMs.T
  object TimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
