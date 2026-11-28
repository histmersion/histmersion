package com.histmersion
package domain.library.sources

import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.database.Transactor
import com.histmersion.domain.PassageFinder
import com.histmersion.domain.catalogue.evidence.passages.{Passage, PassageReader}
import com.histmersion.util.time.Timer
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Title
import com.histmersion.lint.{tolerated, LintRule}

/** The sources' bodies as the catalogue's evidence reads them: by their current citekey, searched chunk by chunk, and
  * the sources that name a phrase (the full-text index's phrase search, a mechanism over SQL).
  */
final class SourcePassages(db: Transactor)(using timer: Timer) extends Passage.Source:
  def canonical(citekey: Citekey): Option[Citekey] = db.read(SourceRepo.canonical(citekey))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def naming(name: Title, limit: PageSize): List[Citekey] =
    db.read(SourceRepo.naming(SourceRepo.Name(name.value), SourceRepo.Limit(limit.value)))
  def reader(citekey: Citekey): PassageReader = SourcePassages.Text(db, citekey)

object SourcePassages:

  final private class Text(db: Transactor, citekey: Citekey)(using timer: Timer) extends PassageReader:
    private lazy val chunks = db.read(SourceRepo.bodyChunks(citekey))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def find(
        query: PassageReader.Query,
        regex: PassageReader.Regex,
        limit: PageSize,
        context: PassageReader.Context,
        maxMatches: PassageReader.MaxMatches,
        timeoutMs: PassageReader.TimeoutMs
    ): Either[Passage.Text, List[Passage]] =
      PassageFinder
        .find(
          citekey,
          chunks,
          PassageFinder.Query(query.value),
          (if regex.value then PassageFinder.Regex.Regex else PassageFinder.Regex.Literal),
          PassageFinder.Limit(limit.value),
          PassageFinder.ContextChars(context.value),
          PassageFinder.MaxMatches(maxMatches.value),
          PassageFinder.TimeoutMs(timeoutMs.value)
        )
        .left
        .map(p => Passage.Text(p.sentence))
        .map(_.passages.map(h => Passage(Passage.Offset(h.offset.value), Passage.Text(h.text.value), h.matches)))
