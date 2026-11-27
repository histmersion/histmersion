package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.domain.catalogue.events.EventSelection

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
  export EventSelection.Kind
  export AssetDetailRow.Caption
  export Known.Uid
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  export AssetDetailRow.License
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends Newtype[Boolean, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends Newtype[String, Pure]
  type LicenseUrl = LicenseUrl.T
  object LicenseUrl extends Newtype[String, Pure]
  type Author = Author.T
  object Author extends Newtype[String, Pure]
