package com.histmersion.util.jobs

import com.histmersion.api.{DeleteBlocker, DeleteBlockerKind, RenameCount}
import com.histmersion.domain.library.sources.referrers.*

import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** The jobs that wrote or write a source: their records follow a rename and stay as history, and a job not finished yet
  * refuses a delete (it would write the source back).
  */
final class JobSourceReferrer extends SourceReferrer:

  override def renamed(rename: SourceRename)(using DbCon): RenamedReferences =
    RenamedReferences(
      List(
        "jobs.citekey" -> sql"UPDATE jobs SET citekey = ${rename.to} WHERE citekey = ${rename.from}".update.run()
      ).collect { case (t, n) if n > 0 => RenameCount(t, n) }
    )

  override def report(source: SourceKeys)(using DbCon): DeleteReport =
    val keys = source.keys
    DeleteReport(blockers =
      sql"""SELECT id, kind FROM jobs
            WHERE status IN ('queued', 'running', 'paused', 'waiting')
              AND (citekey = ANY($keys::text[]) OR (target_kind = 'source' AND target_id = ANY($keys::text[])))
            ORDER BY created_at"""
        .query[(String, String)]
        .run()
        .toList
        .map((id, k) => DeleteBlocker(DeleteBlockerKind.Job, id, Some(k)))
    )
