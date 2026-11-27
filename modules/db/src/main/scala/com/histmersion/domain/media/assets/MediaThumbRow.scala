package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

final private[assets] case class MediaThumbRow(
    uid: MediaThumbRow.Uid,
    role: MediaThumbRow.Role,
    credit: MediaThumbRow.Credit,
    license: Option[MediaThumbRow.License],
    licenseUrl: Option[MediaThumbRow.LicenseUrl],
    sourceUrl: Option[MediaThumbRow.SourceUrl],
    width: Option[MediaThumbRow.Width],
    height: Option[MediaThumbRow.Height]
) derives DbCodec

object MediaThumbRow:
  type Uid = Uid.T
  object Uid extends Newtype[String, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]
  type Credit = Credit.T
  object Credit extends Newtype[String, Pure]
  type License = License.T
  object License extends Newtype[String, Pure]
  type LicenseUrl = LicenseUrl.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object LicenseUrl extends Newtype[String, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends Newtype[String, Pure]
  type Width = Width.T
  object Width extends Newtype[Int, Pure]
  type Height = Height.T
  object Height extends Newtype[Int, Pure]
