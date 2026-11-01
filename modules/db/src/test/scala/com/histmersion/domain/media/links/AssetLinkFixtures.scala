package com.histmersion.domain.media.links

import com.histmersion.api.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The figures' links' repository as the db suites reach it from outside the feature. */
object AssetLinkFixtures:
  def list(
      ds: DataSource,
      citekey: Option[String],
      asset: Option[String],
      targetType: Option[String],
      targetId: Option[String]
  ): AssetLinkList =
    val rows = connect(ds)(AssetLinkRepo.list(citekey, asset, targetType, targetId))
    AssetLinkList.of(rows.size, rows, 0)

  def create(ds: DataSource, in: AssetLinkPut): Either[ApiError, AssetLink] =
    transact(ds):
      if !AssetLinkRepo.figureExists(in.citekey, in.assetName) then
        Left(ApiError(s"No asset '${in.citekey}/${in.assetName}'", Some(Write.CodeNotFound)))
      else Right(AssetLinkRepo.insert(in))
