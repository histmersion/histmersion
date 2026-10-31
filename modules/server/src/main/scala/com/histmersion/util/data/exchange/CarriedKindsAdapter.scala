package com.histmersion.util.data.exchange

import com.histmersion.util.data.DataOwner

/** Today's carried kinds, registered by owner until each owner registers its own. */
object CarriedKindsAdapter:
  def of(owner: DataOwner): List[CarriedKind] = Nil
