package com.histmersion
package api.library.repairacquire

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.managers.ManagerInfo
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Plural
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.library.RepairAcquire.Remove
import com.histmersion.api.library.RepairAcquire.Markdown
import com.histmersion.api.library.RepairAcquire.Move

final case class RepairResult(
    dryRun: RepairResult.DryRun,
    moved: RepairResult.Moved = RepairResult.Moved(0),
    removed: RepairResult.Removed = RepairResult.Removed(0),
    kept: RepairResult.Kept = RepairResult.Kept(0),
    untouched: RepairResult.Untouched = RepairResult.Untouched(0),
    rows: List[RepairRow] = Nil,
    backlog: ManagerInfo.Backlog = ManagerInfo.Backlog(0),
    backlogDays: Option[ManagerInfo.BacklogDays] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RepairResult:
  export RepairRequest.DryRun
  type Moved = Moved.T
  object Moved extends Newtype[Int, Pure]
  type Removed = Removed.T
  object Removed extends Newtype[Int, Pure]
  type Kept = Kept.T
  object Kept extends Newtype[Int, Pure]
  type Untouched = Untouched.T
  object Untouched extends Newtype[Int, Pure]

  extension (r: RepairResult)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown: Markdown =
      val verb = if (r.dryRun) == RepairRequest.DryRun.DryRun then "would " else ""
      val head = s"# Repair of the acquisition list${
          if (r.dryRun) == RepairRequest.DryRun.DryRun then " (dry run: nothing changed)" else ""
        }\n\n" +
        s"- ${verb}move ${Plural.count(Plural.CountN(r.moved.value.toLong), Plural.CountSingular("legal open-access work"))} into the watchers' ingest backlogs\n" +
        s"- ${verb}remove ${Plural.count(Plural.CountN(r.removed.value.toLong), Plural.CountSingular("item"))} already in the corpus\n" +
        s"- keep ${Plural.count(Plural.CountN(r.kept.value.toLong), Plural.CountSingular("sweep item"))} on the list (no legal open-access copy known, or a watcher that does not ingest)\n" +
        s"- ${Plural.count(Plural.CountN(r.untouched.value.toLong), Plural.CountSingular("open acquisition item"))} not created by a watcher's sweep: untouched\n" +
        s"- ${ManagersMarkdown.backlog(r.backlog, r.backlogDays)}"
      val rows = r.rows.map { row =>
        val what = row.action.value match
          case Move   => s"${verb}move to ${row.watcher}" + row.backlogItem.fold("")(i => s" (backlog item #$i)")
          case Remove => s"${verb}remove"
          case _      => "keep"
        s"- #${row.item} https://doi.org/${row.doi} · goal ${row.goals.mkString(", ")} · $what" +
          (if row.detail.value.nonEmpty then s": ${row.detail}" else "")
      }
      Markdown(
        if rows.isEmpty then head + "\n\n_No acquisition item created by a watcher's sweep is open._"
        else head + "\n\n## Items\n\n" + rows.mkString("\n")
      )
