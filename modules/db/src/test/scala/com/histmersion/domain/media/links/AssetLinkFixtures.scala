package com.histmersion
package domain.media.links
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.Write

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.media.AssetLink
import com.histmersion.api.Api
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.media.assets.{AssetInfoRow, MediaItemRow}
import com.histmersion.domain.media.imports.Target
import com.histmersion.api.RowCount
import com.histmersion.api.personal.UnreadPage
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** The figures' links' repository as the db suites reach it from outside the feature. */
object AssetLinkFixtures:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def list(
      ds: DataSource,
      citekey: Option[Citekey],
      asset: Option[MediaItemRow.Uid],
      targetType: Option[Target.TargetType],
      targetId: Option[Target.Id]
  ): AssetLink.List =
    val rows = connect(ds)(AssetLinkRepo.list(citekey, asset, targetType, targetId))
    AssetLink.List.of(RowCount(rows.size), rows, UnreadPage.Offset(0))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def create(ds: DataSource, in: AssetLink.Put): Either[Api.Error, AssetLink] =
    transact(ds):
      if !AssetLinkRepo.figureExists(Citekey.assume(in.citekey.value), AssetInfoRow.Name(in.assetName.value)) then
        Left(
          Api.Error(
            Problem.Unclassified(Legacy.Text(s"No asset '${in.citekey}/${in.assetName}'")),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
      else Right(AssetLinkRepo.insert(in))
