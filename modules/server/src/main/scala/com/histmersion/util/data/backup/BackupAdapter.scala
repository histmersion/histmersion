package com.histmersion.util.data.backup

import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.TablesAdapter.LibraryOwner

/** Today's share of each owner in a backup, until each owner registers its own: the dump holds the database alone (no
  * store's files), and a restore refuses to drop the library's sources.
  */
object BackupAdapter:
  def of(owner: DataOwner): BackupShare = owner match
    case LibraryOwner => BackupShare(guarded = List(GuardedTable(TableName("sources"), "source")))
    case _            => BackupShare.none
