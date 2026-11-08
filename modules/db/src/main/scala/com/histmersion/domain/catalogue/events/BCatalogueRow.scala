package com.histmersion
package domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a catalogue says about an event, as its bundle line reads it. */
final private[events] case class BCatalogueRow(
    eventUid: UUID,
    provider: BCatalogueRow.Provider,
    providerId: BCatalogueRow.ProviderId,
    url: Option[BCatalogueRow.Url],
    label: BCatalogueRow.Label,
    kind: BCatalogueRow.Kind,
    notBefore: Option[BCatalogueRow.NotBefore],
    beginBy: Option[BCatalogueRow.BeginBy],
    endFrom: Option[BCatalogueRow.EndFrom],
    notAfter: Option[BCatalogueRow.NotAfter],
    lat: Option[Latitude],
    lon: Option[Longitude],
    vei: Option[BCatalogueRow.Vei],
    magnitude: Option[BCatalogueRow.Magnitude],
    deaths: Option[BCatalogueRow.Deaths],
    historicity: BCatalogueRow.Historicity,
    record: BCatalogueRow.Record,
    retrievedAt: BCatalogueRow.RetrievedAt
) derives DbCodec

object BCatalogueRow:
  type Provider = Provider.T
  object Provider extends RefinedType[String, Pure]
  type ProviderId = ProviderId.T
  object ProviderId extends RefinedType[String, Pure]
  type Url = Url.T
  object Url extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type NotBefore = NotBefore.T
  object NotBefore extends RefinedType[Long, Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends RefinedType[Long, Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends RefinedType[Long, Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends RefinedType[Long, Pure]
  type Vei = Vei.T
  object Vei extends RefinedType[Int, Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends RefinedType[Double, Pure]
  type Deaths = Deaths.T
  object Deaths extends RefinedType[Long, Pure]
  type Historicity = Historicity.T
  object Historicity extends RefinedType[String, Pure]
  type Record = Record.T
  object Record extends RefinedType[String, Pure]
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends RefinedType[String, Pure]
