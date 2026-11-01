package com.histmersion.util.data.exchange

import com.histmersion.domain.catalogue.CatalogueExchange
import com.histmersion.domain.media.MediaExchange

/** The carried kinds and merge rules of every owner, as the server's registry has them: the adapters' and the
  * catalogue's and the media's own, for the db suites that run an export, an import or a plan without a server.
  */
object TestExchange:
  val carried: List[CarriedKind] = CarriedKindsAdapter.all ++ CatalogueExchange.carried ++ MediaExchange.carried
  val kinds: CarriedKinds = CarriedKinds(carried)
  val rules: MergeRuleSet = MergeRuleSet(
    MergeRulesAdapter.all ++ CatalogueExchange.mergeRules ++ MediaExchange.mergeRules
  )
