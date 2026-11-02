package com.histmersion.util.data.mirror

/** The source notes' records in the mirror, as the composition root reads a source's note through the mirror (the
  * library registers its notes itself, `LibraryData.SourceNotes`, the same records).
  */
object MirrorAdapter:
  val SourceNotes: MirrorRecords = MirrorRecords("sources")
