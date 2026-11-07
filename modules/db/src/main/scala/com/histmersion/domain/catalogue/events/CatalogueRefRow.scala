package com.histmersion.domain.catalogue.events

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given

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
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProviderId = ProviderId.T
  object ProviderId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vei = Vei.T
  object Vei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Deaths = Deaths.T
  object Deaths extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
