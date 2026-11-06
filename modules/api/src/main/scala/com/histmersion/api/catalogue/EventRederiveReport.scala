package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventRederiveReport(
    dryRun: EventRederiveReport.DryRun,
    providers: List[EventRederiveReport.Providers],
    records: EventRederiveReport.Records = _root_.com.histmersion.api.catalogue.EventRederiveReport.Records(0),
    changed: EventRederiveReport.Changed = _root_.com.histmersion.api.catalogue.EventRederiveReport.Changed(0),
    fromRaw: EventRederiveReport.FromRaw = _root_.com.histmersion.api.catalogue.EventRederiveReport.FromRaw(0),
    updated: EventRederiveReport.Updated = _root_.com.histmersion.api.catalogue.EventRederiveReport.Updated(0),
    before: List[EventCount] = Nil,
    after: List[EventCount] = Nil,
    offMap: EventRederiveReport.OffMap = _root_.com.histmersion.api.catalogue.EventRederiveReport.OffMap(0),
    examples: List[EventRederiveReport.Examples] = Nil,
    corrections: Correction.Counts = Correction.Counts()
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object EventRederiveReport:
  type Report = Report.T
  object Report extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  /** An events re-dating job's report, in its detail. */

  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Providers = Providers.T
  object Providers extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Records = Records.T
  object Records extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Changed = Changed.T
  object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromRaw = FromRaw.T
  object FromRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Updated = Updated.T
  object Updated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OffMap = OffMap.T
  object OffMap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Examples = Examples.T
  object Examples extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[EventRederiveReport]] =
    com.histmersion.api.jobs.JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("eventRederive"), None)

  def summary(r: EventRederiveReport): String =
    def counts(cs: List[EventCount]) = cs.map(c => s"${c.key} ${c.n}").mkString(", ")
    (List(
      Some(
        s"${r.providers.mkString(", ")}: ${r.records} record(s) re-derived (${r.fromRaw} from the catalogue's own record)"
      ),
      Some(
        s"${r.changed} dating(s) ${if ((r.dryRun) == _root_.com.histmersion.api.catalogue.EventRederiveReport.DryRun(true)) then "would change" else "changed"}, ${r.updated} event(s) refreshed"
      ),
      Option.when(r.after.nonEmpty)(s"precision ${counts(r.before)} → ${counts(r.after)}"),
      Option.when((r.offMap).value > 0)(s"${r.offMap} imprecise (off the map, on the list)"),
      r.corrections.summary
    ).flatten.mkString(" · "))

  def markdown(r: EventRederiveReport): com.histmersion.api.catalogue.EventRederiveReport.Report =
    val head =
      s"## Event re-derivation — ${r.providers.mkString(", ")}${if (r.dryRun).value then " (dry run: nothing stored)" else ""}"
    _root_.com.histmersion.api.catalogue.EventRederiveReport.Report((head :: "" :: (summary(r)).split(" · ").toList.map("- " + _) ++ r.examples.map("  - " + _)).mkString("\n"))
