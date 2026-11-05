package com.histmersion.domain.library.sources

import com.histmersion.util.database.Transactor
import com.histmersion.domain.PassageFinder
import com.histmersion.domain.catalogue.evidence.passages.{Passage, PassageReader}

/** The sources' bodies as the catalogue's evidence reads them: by their current citekey, searched chunk by chunk, and
  * the sources that name a phrase (the full-text index's phrase search, a mechanism over SQL).
  */
final class SourcePassages(db: Transactor)(using timer: com.histmersion.util.time.Timer) extends Passage.Source:
  def canonical(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.sources.SourcePassages.CanonicalResult] = db.read(SourceRepo.canonical(citekey))
  def naming(name: com.histmersion.domain.library.sources.SourcePassages.NamingName, limit: com.histmersion.domain.library.sources.SourcePassages.NamingLimit): List[com.histmersion.domain.library.sources.SourcePassages.NamingResult] = db.read(SourceRepo.naming(name, limit))
  def reader(citekey: com.histmersion.domain.library.Citekey): PassageReader = SourcePassages.Text(db, citekey)

object SourcePassages:
  /** One source's body, its chunks read on the first search. */
  type CanonicalResult = CanonicalResult.T
  object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamingResult = NamingResult.T
  object NamingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamingName = NamingName.T
  object NamingName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NamingLimit = NamingLimit.T
  object NamingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final private class Text(db: Transactor, citekey: com.histmersion.domain.library.Citekey)(using timer: com.histmersion.util.time.Timer) extends PassageReader:
    private lazy val chunks = db.read(SourceRepo.bodyChunks(citekey))
    def find(
        query: com.histmersion.domain.library.sources.SourcePassages.Text.FindQuery,
        regex: com.histmersion.domain.library.sources.SourcePassages.Text.FindRegex,
        limit: com.histmersion.domain.library.sources.SourcePassages.Text.FindLimit,
        context: com.histmersion.domain.library.sources.SourcePassages.Text.FindContext,
        maxMatches: com.histmersion.domain.library.sources.SourcePassages.Text.FindMaxMatches,
        timeoutMs: com.histmersion.domain.library.sources.SourcePassages.Text.FindTimeoutMs
    ): Either[com.histmersion.domain.library.sources.SourcePassages.Text.FindResultPart1, List[Passage]] =
      PassageFinder
        .find(citekey, chunks, query, regex, limit, context, maxMatches, timeoutMs)
        .map(_.passages.map(h => Passage(h.offset, h.text, h.matches)))

  object Text:
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
