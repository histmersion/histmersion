package com.histmersion.domain.library.sources

import com.histmersion.util.database.Transactor
import com.histmersion.domain.PassageFinder
import com.histmersion.domain.catalogue.evidence.passages.{Passage, PassageReader}

/** The sources' bodies as the catalogue's evidence reads them: by their current citekey, searched chunk by chunk, and
  * the sources that name a phrase (the full-text index's phrase search, a mechanism over SQL).
  */
final class SourcePassages(db: Transactor) extends Passage.Source:
  def canonical(citekey: String): Option[String] = db.read(SourceRepo.canonical(citekey))
  def naming(name: String, limit: Int): List[String] = db.read(SourceRepo.naming(name, limit))
  def reader(citekey: String): PassageReader = SourcePassages.Text(db, citekey)

object SourcePassages:
  /** One source's body, its chunks read on the first search. */
  final private class Text(db: Transactor, citekey: String) extends PassageReader:
    private lazy val chunks = db.read(SourceRepo.bodyChunks(citekey))
    def find(
        query: String,
        regex: Boolean,
        limit: Int,
        context: Int,
        maxMatches: Int,
        timeoutMs: Long
    ): Either[String, List[Passage]] =
      PassageFinder
        .find(citekey, chunks, query, regex, limit, context, maxMatches, timeoutMs)
        .map(_.passages.map(h => Passage(h.offset, h.text, h.matches)))
