package com.histmersion
package domain.media.assets

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
  object Uid extends RefinedType[String, Pure]
  type Role = Role.T
  object Role extends RefinedType[String, Pure]
  type Credit = Credit.T
  object Credit extends RefinedType[String, Pure]
  type License = License.T
  object License extends RefinedType[String, Pure]
  type LicenseUrl = LicenseUrl.T
  object LicenseUrl extends RefinedType[String, Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends RefinedType[String, Pure]
  type Width = Width.T
  object Width extends RefinedType[Int, Pure]
  type Height = Height.T
  object Height extends RefinedType[Int, Pure]
