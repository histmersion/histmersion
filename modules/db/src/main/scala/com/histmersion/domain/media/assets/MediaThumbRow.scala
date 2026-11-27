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
  export Known.Uid
  type Role = Role.T
  object Role extends Newtype[String, Pure]
  export MediaGeoRow.Credit
  export AssetDetailRow.License
  export MediaGeoRow.LicenseUrl
  export MediaGeoRow.SourceUrl
  export AssetDetailRow.Width
  export AssetDetailRow.Height
