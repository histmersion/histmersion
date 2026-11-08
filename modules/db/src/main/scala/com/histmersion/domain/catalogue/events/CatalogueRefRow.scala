package com.histmersion
package domain.catalogue.events

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure

/** A catalogue's row of one event, as its card lists it. */
final private[events] case class CatalogueRefRow(
    provider: CatalogueRefRow.Provider,
    providerId: CatalogueRefRow.ProviderId,
    url: Option[CatalogueRefRow.Url],
    vei: Option[CatalogueRefRow.Vei],
    magnitude: Option[CatalogueRefRow.Magnitude],
    deaths: Option[CatalogueRefRow.Deaths],
    retrievedAt: CatalogueRefRow.RetrievedAt
) derives DbCodec

object CatalogueRefRow:
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type ProviderId = ProviderId.T
  object ProviderId extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]
  type Vei = Vei.T
  object Vei extends Newtype[Int, Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends Newtype[Double, Pure]
  type Deaths = Deaths.T
  object Deaths extends Newtype[Long, Pure]
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends Newtype[String, Pure]
