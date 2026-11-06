package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.given

object RepairAcquire:
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class RepairRequest(dryRun: RepairRequest.DryRun = _root_.com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun(false), by: Option[RepairRequest.By] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RepairRequest:
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = com.histmersion.api.automation.Activity.Actor
    val By: _root_.com.histmersion.api.automation.Activity.Actor.type = _root_.com.histmersion.api.automation.Activity.Actor


  final case class RepairRow(
      item: RepairRow.Item,
      doi: com.histmersion.api.library.Doi,
      goals: List[RepairRow.Goals] = Nil,
      watcher: RepairRow.Watcher = _root_.com.histmersion.api.library.RepairAcquire.RepairRow.Watcher(""),
      action: RepairRow.Action = _root_.com.histmersion.api.library.RepairAcquire.RepairRow.Action(""),
      detail: RepairRow.Detail = _root_.com.histmersion.api.library.RepairAcquire.RepairRow.Detail(""),
      backlogItem: Option[RepairRow.BacklogItem] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RepairRow:
    type Item = com.histmersion.api.library.Acquire.ItemNumber
    val Item: _root_.com.histmersion.api.library.Acquire.ItemNumber.type = _root_.com.histmersion.api.library.Acquire.ItemNumber
    type Goals = Goals.T
    object Goals extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Watcher = Watcher.T
    object Watcher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogItem = BacklogItem.T
    object BacklogItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class RepairResult(
      dryRun: RepairResult.DryRun,
      moved: RepairResult.Moved = _root_.com.histmersion.api.library.RepairAcquire.RepairResult.Moved(0),
      removed: RepairResult.Removed = _root_.com.histmersion.api.library.RepairAcquire.RepairResult.Removed(0),
      kept: RepairResult.Kept = _root_.com.histmersion.api.library.RepairAcquire.RepairResult.Kept(0),
      untouched: RepairResult.Untouched = _root_.com.histmersion.api.library.RepairAcquire.RepairResult.Untouched(0),
      rows: List[RepairRow] = Nil,
      backlog: RepairResult.Backlog = _root_.com.histmersion.api.library.RepairAcquire.RepairResult.Backlog(0),
      backlogDays: Option[RepairResult.BacklogDays] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RepairResult:
    type DryRun = com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun
    val DryRun: _root_.com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun.type = _root_.com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun
    type Moved = Moved.T
    object Moved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Removed = Removed.T
    object Removed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kept = Kept.T
    object Kept extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Untouched = Untouched.T
    object Untouched extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Backlog = Backlog.T
    object Backlog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogDays = BacklogDays.T
    object BacklogDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val Move = "move"
  val Remove = "remove"
  val Keep = "keep"

  def markdown(r: RepairResult): com.histmersion.api.library.RepairAcquire.Markdown =
    val verb = if ((r.dryRun) == _root_.com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun(true)) then "would " else ""
    val head = s"# Repair of the acquisition list${if ((r.dryRun) == _root_.com.histmersion.api.library.RepairAcquire.RepairRequest.DryRun(true)) then " (dry run: nothing changed)" else ""}\n\n" +
      s"- ${verb}move ${Plural.count(_root_.com.histmersion.api.Plural.CountN(r.moved.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("legal open-access work"))} into the watchers' ingest backlogs\n" +
      s"- ${verb}remove ${Plural.count(_root_.com.histmersion.api.Plural.CountN(r.removed.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("item"))} already in the corpus\n" +
      s"- keep ${Plural.count(_root_.com.histmersion.api.Plural.CountN(r.kept.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("sweep item"))} on the list (no legal open-access copy known, or a watcher that does not ingest)\n" +
      s"- ${Plural.count(_root_.com.histmersion.api.Plural.CountN(r.untouched.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("open acquisition item"))} not created by a watcher's sweep: untouched\n" +
      s"- ${ManagersMarkdown.backlog(r.backlog, r.backlogDays)}"
    val rows = r.rows.map { row =>
      val what = row.action.value match
        case Move   => s"${verb}move to ${row.watcher}" + row.backlogItem.fold("")(i => s" (backlog item #$i)")
        case Remove => s"${verb}remove"
        case _      => "keep"
      s"- #${row.item} https://doi.org/${row.doi} · goal ${row.goals.mkString(", ")} · $what" +
        (if row.detail.value.nonEmpty then s": ${row.detail}" else "")
    }
    if rows.isEmpty then head + "\n\n_No acquisition item created by a watcher's sweep is open._"
    else head + "\n\n## Items\n\n" + rows.mkString("\n")
