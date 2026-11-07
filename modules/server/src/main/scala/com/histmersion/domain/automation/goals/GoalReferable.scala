package com.histmersion.domain.automation.goals

import com.histmersion.domain.ItemKind
import com.histmersion.util.database.Transactor
import com.histmersion.util.identity.{AliasColumns, RecordTable, TableReferable}

/** Research goals as others point at them: by slug (and the slugs a rename left), a uid that survives renames. */
final class GoalReferable(db: Transactor) extends TableReferable(db, List(GoalReferable.Table))

object GoalReferable:
  val Kind: ItemKind = ItemKind("goal")

  val Table: RecordTable =
    RecordTable(
      Kind,
      com.histmersion.util.identity.RecordTable.Table("goals"),
      com.histmersion.util.identity.RecordTable.Key("t.slug"),
      com.histmersion.util.identity.RecordTable.Title("t.title"),
      Some(com.histmersion.util.identity.RecordTable.Uid("t.uid")),
      Some(AliasColumns(com.histmersion.util.identity.AliasColumns.Table("goal_aliases"), com.histmersion.util.identity.AliasColumns.Alias("alias"), com.histmersion.util.identity.AliasColumns.Target("goal_slug")))
    )
