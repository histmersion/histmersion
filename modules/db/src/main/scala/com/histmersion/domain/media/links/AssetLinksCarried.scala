package com.histmersion.domain.media.links

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*

/** What a bundle carries of the figures' links: each line stamped with its rev; an import writes them after the blobs,
  * once every figure they name is here.
  */
object AssetLinksCarried:

  val assetLinks: CarriedKind = CarriedKind.records[com.histmersion.api.exchange.Bundle.AssetLink](
    DataOwner("media"),
    Bundle.Kind.AssetLink,
    TableName("asset_links"),
    List(RowsIn(ImportStage.AfterBlobs, ImportRank(102), AssetLinkBundleRepo.assetLinksIn))
  )(scope => AssetLinkBundleRepo.assetLinks(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.assetLinks).setTo(ls))
