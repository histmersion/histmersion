package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.given

object RepairAcquire:

  final case class RepairRequest(dryRun: Boolean = false, by: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object RepairRequest:
    given Schema[RepairRequest] = summon[KindlingsSchema[RepairRequest]].schema

  final case class RepairRow(
      item: Int,
      doi: String,
      goals: List[String] = Nil,
      watcher: String = "",
      action: String = "",
      detail: String = "",
      backlogItem: Option[Long] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RepairRow:
    given Schema[RepairRow] = summon[KindlingsSchema[RepairRow]].schema

  final case class RepairResult(
      dryRun: Boolean,
      moved: Int = 0,
      removed: Int = 0,
      kept: Int = 0,
      untouched: Int = 0,
      rows: List[RepairRow] = Nil,
      backlog: Int = 0,
      backlogDays: Option[Int] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RepairResult:
    given Schema[RepairResult] = summon[KindlingsSchema[RepairResult]].schema

  val Move = "move"
  val Remove = "remove"
  val Keep = "keep"

  def markdown(r: RepairResult): String =
    val verb = if r.dryRun then "would " else ""
    val head = s"# Repair of the acquisition list${if r.dryRun then " (dry run: nothing changed)" else ""}\n\n" +
      s"- ${verb}move ${Plural.count(r.moved, "legal open-access work")} into the watchers' ingest backlogs\n" +
      s"- ${verb}remove ${Plural.count(r.removed, "item")} already in the corpus\n" +
      s"- keep ${Plural.count(r.kept, "sweep item")} on the list (no legal open-access copy known, or a watcher that does not ingest)\n" +
      s"- ${Plural.count(r.untouched, "open acquisition item")} not created by a watcher's sweep: untouched\n" +
      s"- ${ManagersMarkdown.backlog(r.backlog, r.backlogDays)}"
    val rows = r.rows.map { row =>
      val what = row.action match
        case Move   => s"${verb}move to ${row.watcher}" + row.backlogItem.fold("")(i => s" (backlog item #$i)")
        case Remove => s"${verb}remove"
        case _      => "keep"
      s"- #${row.item} https://doi.org/${row.doi} · goal ${row.goals.mkString(", ")} · $what" +
        (if row.detail.nonEmpty then s": ${row.detail}" else "")
    }
    if rows.isEmpty then head + "\n\n_No acquisition item created by a watcher's sweep is open._"
    else head + "\n\n## Items\n\n" + rows.mkString("\n")
