package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobDetail

final case class EventRederiveReport(
    dryRun: EventRederiveReport.DryRun,
    providers: List[EventRederiveReport.Providers],
    records: EventRederiveReport.Records = EventRederiveReport.Records(0),
    changed: EventRederiveReport.Changed = EventRederiveReport.Changed(0),
    fromRaw: EventRederiveReport.FromRaw = EventRederiveReport.FromRaw(0),
    updated: EventRederiveReport.Updated = EventRederiveReport.Updated(0),
    before: List[EventCount] = Nil,
    after: List[EventCount] = Nil,
    offMap: EventRederiveReport.OffMap = EventRederiveReport.OffMap(0),
    examples: List[EventRederiveReport.Examples] = Nil,
    corrections: Correction.Counts = Correction.Counts()
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
object EventRederiveReport:
  type Report = Report.T
  object Report extends Newtype[String, Pure]

  /** An events re-dating job's report, in its detail. */

  type DryRun = DryRun.T
  object DryRun extends Newtype[Boolean, Pure]
  type Providers = Providers.T
  object Providers extends Newtype[String, Pure]
  type Records = Records.T
  object Records extends Newtype[Int, Pure]
  type Changed = Changed.T
  object Changed extends Newtype[Int, Pure]
  type FromRaw = FromRaw.T
  object FromRaw extends Newtype[Int, Pure]
  type Updated = Updated.T
  object Updated extends Newtype[Int, Pure]
  type OffMap = OffMap.T
  object OffMap extends Newtype[Int, Pure]
  type Examples = Examples.T
  object Examples extends Newtype[String, Pure]

  val Detail: JobDetail.Field[Option[EventRederiveReport]] =
    new com.histmersion.api.jobs.JobDetail.Field(JobDetail.Field.Name("eventRederive"), None)

  def summary(r: EventRederiveReport): String =
    def counts(cs: List[EventCount]) = cs.map(c => s"${c.key} ${c.n}").mkString(", ")
    (List(
      Some(
        s"${r.providers.mkString(", ")}: ${r.records} record(s) re-derived (${r.fromRaw} from the catalogue's own record)"
      ),
      Some(
        s"${r.changed} dating(s) ${if (r.dryRun) == DryRun(true) then "would change" else "changed"}, ${r.updated} event(s) refreshed"
      ),
      Option.when(r.after.nonEmpty)(s"precision ${counts(r.before)} → ${counts(r.after)}"),
      Option.when((r.offMap).value > 0)(s"${r.offMap} imprecise (off the map, on the list)"),
      r.corrections.summary
    ).flatten.mkString(" · "))

  def markdown(r: EventRederiveReport): Report =
    val head =
      s"## Event re-derivation — ${r.providers.mkString(", ")}${
          if (r.dryRun).value then " (dry run: nothing stored)" else ""
        }"
    Report((head :: "" :: (summary(r)).split(" · ").toList.map("- " + _) ++ r.examples.map("  - " + _)).mkString("\n"))
