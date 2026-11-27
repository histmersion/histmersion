package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{LintRule, tolerated}

object RepairAcquire:
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  final case class RepairRequest(
      dryRun: RepairRequest.DryRun = RepairRequest.DryRun(false),
      by: Option[RepairRequest.By] = None,
      createdBy: Option[Creation.By] = None,
      via: Option[Creation.Via] = None
  ) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

  object RepairRequest:
    type DryRun = DryRun.T
    object DryRun extends Newtype[Boolean, Pure]
    export api.automation.Activity.Actor as By

  final case class RepairRow(
      item: RepairRow.Item,
      doi: Doi,
      goals: List[RepairRow.Goals] = Nil,
      watcher: RepairRow.Watcher = RepairRow.Watcher(""),
      action: RepairRow.Action = RepairRow.Action(""),
      detail: RepairRow.Detail = RepairRow.Detail(""),
      backlogItem: Option[RepairRow.BacklogItem] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object RepairRow:
    export Acquire.ItemNumber as Item
    type Goals = Goals.T
    object Goals extends Newtype[String, Pure]
    type Watcher = Watcher.T
    object Watcher extends Newtype[String, Pure]
    type Action = Action.T
    object Action extends Newtype[String, Pure]
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]
    type BacklogItem = BacklogItem.T
    object BacklogItem extends Newtype[Long, Pure]

  final case class RepairResult(
      dryRun: RepairResult.DryRun,
      moved: RepairResult.Moved = RepairResult.Moved(0),
      removed: RepairResult.Removed = RepairResult.Removed(0),
      kept: RepairResult.Kept = RepairResult.Kept(0),
      untouched: RepairResult.Untouched = RepairResult.Untouched(0),
      rows: List[RepairRow] = Nil,
      backlog: Managers.ManagerInfo.Backlog = Managers.ManagerInfo.Backlog(0),
      backlogDays: Option[Managers.ManagerInfo.BacklogDays] = None
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

  val Move = "move"
  val Remove = "remove"
  val Keep = "keep"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown(r: RepairResult): Markdown =
    val verb = if (r.dryRun) == RepairRequest.DryRun(true) then "would " else ""
    val head = s"# Repair of the acquisition list${
        if (r.dryRun) == RepairRequest.DryRun(true) then " (dry run: nothing changed)" else ""
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
