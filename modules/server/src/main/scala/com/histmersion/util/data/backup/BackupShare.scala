package com.histmersion.util.data.backup

import com.histmersion.util.storage.Location

/** What a backup takes of an owner beside the database dump, and what a restore must not drop unasked: the stores whose
  * files go with the dump (the dump is the database alone, so no store goes), and the tables a restore refuses to
  * replace while they hold rows.
  */
final case class BackupShare(stores: List[Location] = Nil, guarded: List[GuardedTable] = Nil)

object BackupShare:
  val none: BackupShare = BackupShare()
