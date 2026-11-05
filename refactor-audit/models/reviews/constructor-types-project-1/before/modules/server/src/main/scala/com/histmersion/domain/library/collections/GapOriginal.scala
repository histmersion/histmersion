package com.histmersion.domain.library.collections

import com.histmersion.api.library.CalibreGap
import com.histmersion.api.library.CollectionGapRow

/** Where the file a push would add comes from, as the gaps and the sync report it. */
enum GapOriginal(val label: String):
  /** A host-side copy (`cli.calibre.originals-dir`): the CLI's own classification, never the server's. */
  case Local extends GapOriginal("local")

  /** The original the server stores. */
  case Stored extends GapOriginal("stored")

  /** The server did not say: counted, and the push tries. */
  case Unknown extends GapOriginal("unknown")

  /** No file anywhere — a metadata-only record: nothing to push, so not a gap. */
  case Missing extends GapOriginal("none")

object GapOriginal:
  /** A gap counts when there is an original to push and the source is not already in Calibre (provenance `calibre`, or
    * a Calibre id). A record with no original anywhere is listed, never counted.
    */
  def row(g: CalibreGap): CollectionGapRow =
    val original = g.storedOriginal match
      case Some(true)  => Stored
      case Some(false) => Missing
      case None        => Unknown
    val inCalibre = g.calibreId.exists(_.trim.nonEmpty) ||
      g.provenance.exists(_.trim.toLowerCase(java.util.Locale.ROOT) == "calibre")
    CollectionGapRow(
      g.citekey,
      g.title,
      g.authors,
      g.doi,
      g.year,
      g.venue,
      g.summary,
      g.topics,
      g.provenance,
      g.calibreId,
      g.isbn,
      g.storedOriginal,
      original.label,
      counted = original != Missing && !inCalibre
    )
