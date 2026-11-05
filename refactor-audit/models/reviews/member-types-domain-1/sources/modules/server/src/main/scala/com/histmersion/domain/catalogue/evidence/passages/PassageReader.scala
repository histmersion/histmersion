package com.histmersion.domain.catalogue.evidence.passages

/** One source's text, searched for passages: a phrase or a regular expression, each hit with its context; `limit`
  * passages at most.
  */
trait PassageReader:
  def find(
      query: com.histmersion.domain.catalogue.evidence.passages.PassageReader.FindQuery,
      regex: com.histmersion.domain.catalogue.evidence.passages.PassageReader.FindRegex,
      limit: com.histmersion.domain.catalogue.evidence.passages.PassageReader.FindLimit,
      context: com.histmersion.domain.catalogue.evidence.passages.PassageReader.FindContext,
      maxMatches: com.histmersion.domain.catalogue.evidence.passages.PassageReader.FindMaxMatches,
      timeoutMs: com.histmersion.domain.catalogue.evidence.passages.PassageReader.FindTimeoutMs
  ): Either[com.histmersion.domain.catalogue.evidence.passages.PassageReader.FindResultPart1, List[Passage]]

object PassageReader:
  type FindResultPart1 = FindResultPart1.T
  object FindResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindQuery = FindQuery.T
  object FindQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindRegex = FindRegex.T
  object FindRegex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindLimit = FindLimit.T
  object FindLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindContext = FindContext.T
  object FindContext extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindMaxMatches = FindMaxMatches.T
  object FindMaxMatches extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindTimeoutMs = FindTimeoutMs.T
  object FindTimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
