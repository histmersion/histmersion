package com.histmersion.util.data.checks

import com.histmersion.util.data.DataOwner

/** Today's lint rules, registered by owner until each owner registers its own. */
object CheckRulesAdapter:
  def of(owner: DataOwner): List[CheckRule] = Nil
