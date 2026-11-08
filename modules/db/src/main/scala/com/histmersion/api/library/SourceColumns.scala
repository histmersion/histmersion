package com.histmersion
package api.library

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.Vocab

private[histmersion] object SourceColumns:
  given apiStatus: DbCodec[Source.Status] =
    Vocab.codec(
      s => Source.Status.values.find(_.wire.value.equalsIgnoreCase(s.value)),
      e => WireEnumValues.Wire(e.wire.value),
      Source.Status.Unassessed
    )

  given apiConfidence: DbCodec[Option[Confidence]] = DbCodec[Option[String]].biMap(
    _.flatMap(s => Confidence.fromString(Confidence.Wire(s.trim))),
    _.map(_.wire.value)
  )

  given apiEdgeKind: DbCodec[Edge.Kind] =
    Vocab.codec(s => Edge.Kind.fromWire(Edge.Kind.Wire(s.value)), e => WireEnumValues.Wire(Edge.Kind.wireOf(e).value), Edge.Kind.References)
