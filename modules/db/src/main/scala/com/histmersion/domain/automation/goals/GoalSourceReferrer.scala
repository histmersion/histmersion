package com.histmersion.domain.automation.goals

import com.histmersion.api.{DeleteCount, DeletePart}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** The goals' links to a source, which go with it. */
final class GoalSourceReferrer extends SourceReferrer:

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val links = sql"SELECT count(*)::int FROM goal_links WHERE kind = 'source' AND ref = ANY(${source.keys}::text[])"
      .query[Int]
      .run()
      .headOption
      .getOrElse(0)
    DeleteReport(counts = List(DeletePart.GoalLinks -> links).collect {
      case (p, rows) if rows > 0 => DeleteCount(p, rows)
    })

  override def deleting(source: SourceKeys, report: DeleteReport)(using DbCon): Unit =
    sql"DELETE FROM goal_links WHERE kind = 'source' AND ref = ANY(${source.keys}::text[])".update.run()
