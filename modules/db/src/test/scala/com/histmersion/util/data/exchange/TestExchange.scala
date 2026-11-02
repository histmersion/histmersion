package com.histmersion.util.data.exchange

import com.histmersion.domain.automation.AutomationExchange
import com.histmersion.domain.automation.worklists.WorkListMergeFollowUps
import com.histmersion.domain.catalogue.CatalogueExchange
import com.histmersion.domain.library.LibraryExchange
import com.histmersion.domain.media.MediaExchange
import com.histmersion.domain.research.ResearchExchange
import com.histmersion.util.identity.TombstonesCarried

/** The carried kinds, merge rules and merge follow-ups of every owner, as the server's registry has them (each owner's
  * own, in the order of the owners), for the db suites that run an export, an import or a plan without a server.
  */
object TestExchange:
  val carried: List[CarriedKind] =
    AutomationExchange.carried ++ CatalogueExchange.carried ++ MediaExchange.carried ++ LibraryExchange.carried ++
      ResearchExchange.carried :+ TombstonesCarried.tombstones
  val kinds: CarriedKinds = CarriedKinds(carried)
  val rules: MergeRuleSet = MergeRuleSet(
    AutomationExchange.mergeRules ++ CatalogueExchange.mergeRules ++ MediaExchange.mergeRules ++
      LibraryExchange.mergeRules ++ ResearchExchange.mergeRules
  )
  val followUps: MergeFollowUps = WorkListMergeFollowUps
