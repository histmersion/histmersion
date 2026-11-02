package com.histmersion.util.data.backup

import com.histmersion.util.data.DataOwner

/** Today's share of each owner in a backup, until each owner registers its own: the dump holds the database alone (no
  * store's files). The library registers what a restore must not drop unasked (its sources) itself.
  */
object BackupAdapter:
  def of(owner: DataOwner): BackupShare = BackupShare.none
