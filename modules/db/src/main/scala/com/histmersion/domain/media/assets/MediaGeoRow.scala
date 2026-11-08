package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final private[assets] case class MediaGeoRow(
    citekey: Option[Citekey],
    name: MediaGeoRow.Name,
    kind: Option[MediaGeoRow.Kind],
    lat: Latitude,
    lon: Longitude,
    periodStart: Option[Year],
    periodEnd: Option[Year],
    caption: Option[MediaGeoRow.Caption],
    uid: MediaGeoRow.Uid,
    provider: Option[MediaGeoRow.Provider],
    license: Option[MediaGeoRow.License],
    credit: Option[MediaGeoRow.Credit],
    hasThumb: MediaGeoRow.HasThumb,
    sourceUrl: Option[MediaGeoRow.SourceUrl],
    licenseUrl: Option[MediaGeoRow.LicenseUrl],
    author: Option[MediaGeoRow.Author]
) derives DbCodec

object MediaGeoRow:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Caption = Caption.T
  object Caption extends RefinedType[String, Pure]
  type Uid = Uid.T
  object Uid extends RefinedType[String, Pure]
  type Provider = Provider.T
  object Provider extends RefinedType[String, Pure]
  type License = License.T
  object License extends RefinedType[String, Pure]
  type Credit = Credit.T
  object Credit extends RefinedType[String, Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends RefinedType[Boolean, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends RefinedType[String, Pure]
  type LicenseUrl = LicenseUrl.T
  object LicenseUrl extends RefinedType[String, Pure]
  type Author = Author.T
  object Author extends RefinedType[String, Pure]
