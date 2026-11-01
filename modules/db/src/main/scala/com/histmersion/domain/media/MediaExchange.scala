package com.histmersion.domain.media

import com.histmersion.domain.media.assets.{AssetMergeRules, AssetsCarried}
import com.histmersion.domain.media.links.{AssetLinkMergeRules, AssetLinksCarried}
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}

/** What the media carries in a bundle and how a merge treats its records: the figures and their links. */
object MediaExchange:

  /** The figures, then their links. */
  val carried: List[CarriedKind] = List(AssetsCarried, AssetLinksCarried.assetLinks)

  /** How a merge treats figures and their links. */
  val mergeRules: List[MergeRules] = List(AssetMergeRules, AssetLinkMergeRules)
