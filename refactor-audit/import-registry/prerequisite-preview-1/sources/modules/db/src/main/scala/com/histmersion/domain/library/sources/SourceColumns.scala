package com.histmersion.domain.library.sources

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Source
import com.histmersion.util.database.Vocab

private[library] object SourceColumns:
  /** An unknown status is `unassessed` — nobody has judged it. */
  given given_DbCodec_SourceStatus: DbCodec[Source.Status] =
    Vocab.codec(s => Source.Status.values.find(_.wire.equalsIgnoreCase(s)), _.wire, Source.Status.Unassessed)
  
  given apiStatus: DbCodec[com.histmersion.api.library.Source.Status] =
    Vocab.codec(
      s => com.histmersion.api.library.Source.Status.values.find(_.wire.equalsIgnoreCase(s)),
      _.wire,
      com.histmersion.api.library.Source.Status.Unassessed
    )
