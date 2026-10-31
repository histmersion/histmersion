package com.histmersion.util.data.mirror

import com.histmersion.util.data.DataOwner

/** Today's mirror notes, registered by owner until each owner registers its own. */
object MirrorAdapter:
  def of(owner: DataOwner): List[MirrorExport] = Nil
