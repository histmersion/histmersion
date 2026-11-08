package com.histmersion
package domain.library.collections

import com.histmersion.api.library.CalibreGap
import com.histmersion.api.library.CollectionGapRow
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.AuthorName

/** Where the file a push would add comes from, as the gaps and the sync report it. */
enum GapOriginal(val label: GapOriginal.Label) derives CanEqual:
  /** A host-side copy (`cli.calibre.originals-dir`): the CLI's own classification, never the server's. */
  case Local extends GapOriginal(GapOriginal.Label("local"))

  /** The original the server stores. */
  case Stored extends GapOriginal(GapOriginal.Label("stored"))

  /** The server did not say: counted, and the push tries. */
  case Unknown extends GapOriginal(GapOriginal.Label("unknown"))

  /** No file anywhere — a metadata-only record: nothing to push, so not a gap. */
  case Missing extends GapOriginal(GapOriginal.Label("none"))

object GapOriginal:
  /** A gap counts when there is an original to push and the source is not already in Calibre (provenance `calibre`, or
    * a Calibre id). A record with no original anywhere is listed, never counted.
    */
  type Label = Label.T
  object Label extends Newtype[String, Pure]

  def row(g: CalibreGap): CollectionGapRow =
    val original = g.storedOriginal match
      case Some(stored) if stored.value => Stored
      case Some(_)                      => Missing
      case None                         => Unknown
    val inCalibre = g.calibreId.exists(_.value.trim.nonEmpty) ||
      g.provenance.exists(_.value.trim.toLowerCase(java.util.Locale.ROOT) == "calibre")
    CollectionGapRow(
      g.citekey,
      g.title,
      g.authors.map(a => AuthorName(a.value)),
      g.doi,
      g.year,
      g.venue,
      g.summary.map(x => CollectionGapRow.Summary(x.value)),
      g.topics.map(t => CollectionGapRow.Topics(t.value)),
      g.provenance.map(p => CollectionGapRow.Provenance(p.value)),
      g.calibreId,
      g.isbn.map(i => CollectionGapRow.Isbn(i.value)),
      g.storedOriginal.map(s => CollectionGapRow.StoredOriginal(s.value)),
      CollectionGapRow.Original(original.label.value),
      counted = CollectionGapRow.Counted(original != Missing && !inCalibre)
    )
