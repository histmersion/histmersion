package com.histmersion.domain.library.sources

import com.histmersion.util.database.Transactor
import com.histmersion.domain.PassageFinder
import com.histmersion.domain.catalogue.evidence.passages.{Passage, PassageReader}

/** The sources' bodies as the catalogue's evidence reads them: by their current citekey, searched chunk by chunk, and
  * the sources that name a phrase (the full-text index's phrase search, a mechanism over SQL).
  */
final class SourcePassages(db: Transactor)(using timer: com.histmersion.util.time.Timer) extends Passage.Source:
  def canonical(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.Citekey] = db.read(SourceRepo.canonical(citekey))
  def naming(name: com.histmersion.domain.library.sources.SourcePassages.Name, limit: com.histmersion.domain.library.sources.SourcePassages.Limit): List[com.histmersion.domain.library.sources.SourcePassages.NamingResult] = db.read(SourceRepo.naming(name, limit))
  def reader(citekey: com.histmersion.domain.library.Citekey): PassageReader = SourcePassages.Text(db, citekey)

object SourcePassages:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** One source's body, its chunks read on the first search. */
  type NamingResult = NamingResult.T
  object NamingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final private class Text(db: Transactor, citekey: com.histmersion.domain.library.Citekey)(using timer: com.histmersion.util.time.Timer) extends PassageReader:
    private lazy val chunks = db.read(SourceRepo.bodyChunks(citekey))
    def find(
        query: com.histmersion.domain.library.search.Search.Query,
        regex: com.histmersion.domain.library.sources.SourcePassages.Text.FindRegex,
        limit: com.histmersion.domain.library.sources.SourcePassages.Text.FindLimit,
        context: com.histmersion.domain.library.sources.SourcePassages.Text.FindContext,
        maxMatches: com.histmersion.domain.library.sources.SourcePassages.Text.FindMaxMatches,
        timeoutMs: com.histmersion.domain.library.sources.SourcePassages.Text.TimeoutMs
    ): Either[com.histmersion.domain.library.sources.SourcePassages.Problem, List[Passage]] =
      PassageFinder
        .find(citekey, chunks, query, regex, limit, context, maxMatches, timeoutMs)
        .map(_.passages.map(h => Passage(h.offset, h.text, h.matches)))

  object Text:
    type TimeoutMs = TimeoutMs.T
    object TimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindRegex = FindRegex.T
    object FindRegex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindLimit = FindLimit.T
    object FindLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindContext = FindContext.T
    object FindContext extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindMaxMatches = FindMaxMatches.T
    object FindMaxMatches extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
