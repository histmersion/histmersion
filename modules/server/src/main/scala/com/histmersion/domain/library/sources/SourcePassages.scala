package com.histmersion.domain.library.sources

import com.histmersion.util.database.Transactor
import com.histmersion.domain.PassageFinder
import com.histmersion.domain.catalogue.evidence.passages.{Passage, PassageReader}

/** The sources' bodies as the catalogue's evidence reads them: by their current citekey, searched chunk by chunk, and
  * the sources that name a phrase (the full-text index's phrase search, a mechanism over SQL).
  */
final class SourcePassages(db: Transactor)(using timer: com.histmersion.util.time.Timer) extends Passage.Source:
  def canonical(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.Citekey] = db.read(SourceRepo.canonical(citekey))
  def naming(name: com.histmersion.domain.Title, limit: com.histmersion.PageSize): List[com.histmersion.domain.library.Citekey] =
    db.read(SourceRepo.naming(SourceRepo.Name(name.value), SourceRepo.Limit(limit.value)))
  def reader(citekey: com.histmersion.domain.library.Citekey): PassageReader = SourcePassages.Text(db, citekey)

object SourcePassages:

  final private class Text(db: Transactor, citekey: com.histmersion.domain.library.Citekey)(using timer: com.histmersion.util.time.Timer) extends PassageReader:
    private lazy val chunks = db.read(SourceRepo.bodyChunks(citekey))
    def find(
        query: PassageReader.Query,
        regex: PassageReader.Regex,
        limit: com.histmersion.PageSize,
        context: PassageReader.Context,
        maxMatches: PassageReader.MaxMatches,
        timeoutMs: PassageReader.TimeoutMs
    ): Either[Passage.Text, List[Passage]] =
      PassageFinder
        .find(
          citekey,
          chunks,
          PassageFinder.Query(query.value),
          PassageFinder.Regex(regex.value),
          PassageFinder.Limit(limit.value),
          PassageFinder.ContextChars(context.value),
          PassageFinder.MaxMatches(maxMatches.value),
          PassageFinder.TimeoutMs(timeoutMs.value)
        )
        .left
        .map(p => Passage.Text(p.value))
        .map(_.passages.map(h => Passage(Passage.Offset(h.offset.value), Passage.Text(h.text.value), h.matches)))
