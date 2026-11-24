package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

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
  type Uid = Uid.T
  object Uid extends Newtype[String, Pure]
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
  type Width = Width.T
  object Width extends Newtype[Int, Pure]
  type Height = Height.T
  object Height extends Newtype[Int, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Caption = Caption.T
  object Caption extends Newtype[String, Pure]
  type License = License.T
  object License extends Newtype[String, Pure]
  type LicenseUrl = LicenseUrl.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object LicenseUrl extends Newtype[String, Pure]
  type Author = Author.T
  object Author extends Newtype[String, Pure]
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends Newtype[String, Pure]
  type OriginalUrl = OriginalUrl.T
  object OriginalUrl extends Newtype[String, Pure]
  type RetrievedAt = RetrievedAt.T
  object RetrievedAt extends Newtype[String, Pure]
  type HasThumb = HasThumb.T
  object HasThumb extends Newtype[Boolean, Pure]
