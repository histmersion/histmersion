package com.histmersion.domain.library.sources

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Source
import com.histmersion.util.database.Vocab

private[library] object SourceColumns:
  /** An unknown status is `unassessed` — nobody has judged it. */
  given given_DbCodec_SourceStatus: DbCodec[Source.Status] =
    Vocab.codec(
      s => Source.Status.values.find(_.wire.value.equalsIgnoreCase(s.value)),
      e => com.histmersion.WireEnumValues.Wire(e.wire.value),
      Source.Status.Unassessed
    )
