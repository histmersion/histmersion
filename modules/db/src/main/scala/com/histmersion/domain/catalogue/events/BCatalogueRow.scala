package com.histmersion
package domain.catalogue.events

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

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
  object Provider extends Newtype[String, Pure]
  type ProviderId = ProviderId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ProviderId extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type NotBefore = NotBefore.T
  object NotBefore extends Newtype[Long, Pure]
  type BeginBy = BeginBy.T
  object BeginBy extends Newtype[Long, Pure]
  type EndFrom = EndFrom.T
  object EndFrom extends Newtype[Long, Pure]
  type NotAfter = NotAfter.T
  object NotAfter extends Newtype[Long, Pure]
  type Vei = Vei.T
  object Vei extends Newtype[Int, Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends Newtype[Double, Pure]
  type Deaths = Deaths.T
  object Deaths extends Newtype[Long, Pure]
  type Historicity = Historicity.T
  object Historicity extends Newtype[String, Pure]
  type Record = Record.T
  object Record extends Newtype[String, Pure]
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends Newtype[String, Pure]
