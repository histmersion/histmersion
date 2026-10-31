package com.histmersion.util.data.exchange

import com.histmersion.util.data.DataOwner

/** Today's merge rules, registered by owner until each owner registers its own. */
object MergeRulesAdapter:
  def of(owner: DataOwner): List[MergeRules] = Nil
