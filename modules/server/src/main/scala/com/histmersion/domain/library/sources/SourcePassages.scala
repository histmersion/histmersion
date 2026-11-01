package com.histmersion.domain.library.sources

import com.histmersion.db.SourceRepo
import com.histmersion.domain.PassageFinder
import com.histmersion.domain.catalogue.evidence.passages.{Passage, PassageReader, PassageSource}

import javax.sql.DataSource

/** The sources' bodies as the catalogue's evidence reads them: by their current citekey, searched chunk by chunk. */
final class SourcePassages(ds: DataSource) extends PassageSource:
  def canonical(citekey: String): Option[String] = SourceRepo.canonical(ds, citekey)

  def reader(citekey: String): PassageReader =
    new PassageReader:
      private lazy val chunks = SourceRepo.bodyChunks(ds, citekey)
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
