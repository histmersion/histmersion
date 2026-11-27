package com.histmersion
package domain.automation.goals

import com.histmersion.api.library.{DeleteCount, DeletePart}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.util.database.Unused
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The goals' links to a source, which go with it. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "counts and removes the goal rows that point at a deleted source")
final class GoalSourceReferrer extends SourceReferrer:

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val links = sql"SELECT count(*)::int FROM goal_links WHERE kind = 'source' AND ref = ANY(${source.keys}::text[])"
      .query[Int]
      .run()
      .headOption
      .getOrElse(0)
    DeleteReport(counts = List(DeletePart.GoalLinks -> links).collect {
      case (p, rows) if rows > 0 => DeleteCount(p, DeleteCount.Rows(rows))
    })

  override def deleting(source: SourceKeys, report: DeleteReport)(using DbCon): Unit =
    Unused(
      sql"DELETE FROM goal_links WHERE kind = 'source' AND ref = ANY(${source.keys}::text[])".update.run(),
      Unused.Why.Idempotent
    )
