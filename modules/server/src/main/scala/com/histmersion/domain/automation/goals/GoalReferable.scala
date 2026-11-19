package com.histmersion
package domain.automation.goals

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{AliasColumns, RecordTable, TableReferable}
import com.augustnagro.magnum.sql

/** Research goals as others point at them: by slug (and the slugs a rename left), a uid that survives renames. */
final class GoalReferable(db: Transactor) extends TableReferable(db, List(GoalReferable.Table))

object GoalReferable:
  val Kind: ItemKind = ItemKind("goal")

  val Table: RecordTable =
    RecordTable(
      Kind,
      RecordTable.Table("goals"),
      RecordTable.Key("t.slug"),
      RecordTable.Title("t.title"),
      Some(RecordTable.Uid("t.uid")),
      Some(
        AliasColumns(sql"goal_aliases", sql"alias", sql"goal_slug")
      )
    )
