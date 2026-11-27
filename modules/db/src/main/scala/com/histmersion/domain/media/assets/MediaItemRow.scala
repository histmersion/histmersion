package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.domain.catalogue.events.EventSelection

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final private[assets] case class MediaItemRow(
    uid: MediaItemRow.Uid,
    name: MediaItemRow.Name,
    mime: MediaItemRow.Mime,
    bytes: MediaItemRow.Bytes,
    citekey: Option[Citekey],
    provider: Option[MediaItemRow.Provider],
    providerKey: Option[MediaItemRow.ProviderKey],
    width: Option[MediaItemRow.Width],
    height: Option[MediaItemRow.Height],
    kind: Option[MediaItemRow.Kind],
    caption: Option[MediaItemRow.Caption],
    license: Option[MediaItemRow.License],
    licenseUrl: Option[MediaItemRow.LicenseUrl],
    author: Option[MediaItemRow.Author],
    credit: Option[MediaItemRow.Credit],
    sourceUrl: Option[MediaItemRow.SourceUrl],
    originalUrl: Option[MediaItemRow.OriginalUrl],
    retrievedAt: Option[MediaItemRow.RetrievedAt],
    hasThumb: MediaItemRow.HasThumb
) derives DbCodec

object MediaItemRow:
  export Known.Uid
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Mime = Mime.T
  object Mime extends Newtype[String, Pure]
  type Bytes = Bytes.T
  object Bytes extends Newtype[Int, Pure]
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  type ProviderKey = ProviderKey.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ProviderKey extends Newtype[String, Pure]
  export AssetDetailRow.Width
  export AssetDetailRow.Height
  export EventSelection.Kind
  export AssetDetailRow.Caption
  export AssetDetailRow.License
  export MediaGeoRow.LicenseUrl
  export MediaGeoRow.Author
  export MediaGeoRow.Credit
  export MediaGeoRow.SourceUrl
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends Newtype[String, Pure]
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends Newtype[String, Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends Newtype[Boolean, Pure]
