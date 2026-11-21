package com.histmersion
package util.data.backup

import com.histmersion.util.data.TableName
import com.histmersion.util.storage.Location

final case class BackupShare(
    stores: List[Location] = Nil,
    guarded: List[GuardedTable] = Nil,
    rowsLeftOut: List[TableName] = Nil
)

object BackupShare:
  val none: BackupShare = BackupShare()
