package com.histmersion.util.data.backup

import com.histmersion.util.data.TableName

/** A table whose rows a restore refuses to drop unless forced: `noun` names one of them in that refusal (`source`). */
final case class GuardedTable(table: TableName, noun: String)
