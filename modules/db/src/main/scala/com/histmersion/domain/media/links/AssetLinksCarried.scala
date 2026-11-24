package com.histmersion
package domain.media.links

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{LintRule, tolerated}

/** What a bundle carries of the figures' links: each line stamped with its rev; an import writes them after the blobs,
  * once every figure they name is here.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object AssetLinksCarried:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val assetLinks: CarriedKind = CarriedKind.records[Bundle.AssetLink](
    DataOwner("media"),
    Bundle.Kind.AssetLink,
    TableName("asset_links"),
    List(RowsIn(ImportStage.AfterBlobs, ImportRank(102), AssetLinkBundleRepo.assetLinksIn))
  )(scope => AssetLinkBundleRepo.assetLinks(scope).toList)(
    l => CarriedKind.RecordsClock(l.updatedAt.value),
    (es, ls) => es.modify(_.assetLinks).setTo(ls)
  )
