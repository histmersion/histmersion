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
      "goals",
      "t.slug",
      "t.title",
      Some("t.uid"),
      Some(AliasColumns("goal_aliases", "alias", "goal_slug"))
    )
