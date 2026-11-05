package com.histmersion.api.library

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.Vocab

private[histmersion] object SourceColumns:
  given apiStatus: DbCodec[Source.Status] =
    Vocab.codec(s => Source.Status.values.find(_.wire.equalsIgnoreCase(s)), _.wire, Source.Status.Unassessed)
