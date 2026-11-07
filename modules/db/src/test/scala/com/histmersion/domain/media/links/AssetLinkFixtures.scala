package com.histmersion.domain.media.links

import com.histmersion.api.Write

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.media.AssetLink
import com.histmersion.api.Api

/** The figures' links' repository as the db suites reach it from outside the feature. */
object AssetLinkFixtures:
  def list(
      ds: DataSource,
      citekey: Option[String],
      asset: Option[String],
      targetType: Option[String],
      targetId: Option[String]
  ): AssetLink.List =
    val rows = connect(ds)(AssetLinkRepo.list(citekey, asset, targetType, targetId))
    AssetLink.List.of(rows.size, rows, com.histmersion.api.personal.UnreadPage.Offset(0))

  def create(ds: DataSource, in: AssetLink.Put): Either[Api.Error, AssetLink] =
    transact(ds):
      if !AssetLinkRepo.figureExists(in.citekey, in.assetName) then
        Left(Api.Error(s"No asset '${in.citekey}/${in.assetName}'", Some(Write.CodeNotFound)))
      else Right(AssetLinkRepo.insert(in))
