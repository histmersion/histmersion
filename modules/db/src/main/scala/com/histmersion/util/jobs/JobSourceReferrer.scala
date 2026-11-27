package com.histmersion
package util.jobs

import com.histmersion.api.library.DeleteBlocker
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Rename
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.{CreatedAt, Creator}
import com.histmersion.lint.{atBoundary, tolerated, BoundaryKind, LintRule}

/** The jobs that wrote or write a source: their records follow a rename and stay as history, and a job not finished yet
  * refuses a delete (it would write the source back).
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@atBoundary(BoundaryKind.Sql, "counts and removes the job rows that point at a deleted source")
final class JobSourceReferrer extends SourceReferrer:

  override def renamed(rename: SourceRename)(using DbCon)(using Creator.New, CreatedAt): RenamedReferences =
    RenamedReferences(
      List(
        "jobs.citekey" -> sql"UPDATE jobs SET citekey = ${rename.to} WHERE citekey = ${rename.from}".update.run()
      ).collect { case (t, n) if n > 0 => Rename.Count(Rename.Count.Table(t), Rename.Count.Rows(n)) }
    )

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val keys = source.keys
    DeleteReport(blockers =
      sql"""SELECT id, kind FROM jobs
            WHERE status IN ('queued', 'running', 'paused', 'waiting')
              AND (citekey = ANY($keys::text[]) OR (target_kind = 'source' AND target_id = ANY($keys::text[])))
            ORDER BY created_at, id"""
        .query[(DeleteBlocker.Id, DeleteBlocker.Title)]
        .run()
        .toList
        .map((id, k) => DeleteBlocker(DeleteBlocker.Kind.Job, id, Some(k)))
    )
