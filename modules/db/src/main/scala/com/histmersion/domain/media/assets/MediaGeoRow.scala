package com.histmersion.domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given

final private[assets] case class MediaGeoRow(
    citekey: Option[com.histmersion.domain.library.Citekey],
    name: MediaGeoRow.Name,
    kind: Option[MediaGeoRow.Kind],
    lat: com.histmersion.Latitude,
    lon: com.histmersion.Longitude,
    periodStart: Option[com.histmersion.Year],
    periodEnd: Option[com.histmersion.Year],
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
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Caption = Caption.T
  object Caption extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Uid = Uid.T
  object Uid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type License = License.T
  object License extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Credit = Credit.T
  object Credit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LicenseUrl = LicenseUrl.T
  object LicenseUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Author = Author.T
  object Author extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
