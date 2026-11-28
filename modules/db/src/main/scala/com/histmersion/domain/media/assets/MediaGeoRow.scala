package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
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
  object Name extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Caption = Caption.T
  object Caption extends Newtype[String, Pure]
  export MediaItemRow.Uid
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type License = License.T
  object License extends Newtype[String, Pure]
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends Newtype[Boolean, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends Newtype[String, Pure]
  type LicenseUrl = LicenseUrl.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object LicenseUrl extends Newtype[String, Pure]
  type Author = Author.T
  object Author extends Newtype[String, Pure]
