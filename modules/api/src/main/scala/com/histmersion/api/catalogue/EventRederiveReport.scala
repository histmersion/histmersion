package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventRederiveReport(
    dryRun: Boolean,
    providers: List[String],
    records: Int = 0,
    changed: Int = 0,
    fromRaw: Int = 0,
    updated: Int = 0,
    before: List[EventCount] = Nil,
    after: List[EventCount] = Nil,
    offMap: Int = 0,
    examples: List[String] = Nil,
    corrections: Correction.Counts = Correction.Counts()
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventRederiveReport:
  given Schema[EventRederiveReport] = summon[KindlingsSchema[EventRederiveReport]].schema

  /** An events re-dating job's report, in its detail. */
  val Detail: com.histmersion.api.jobs.JobDetail.Field[Option[EventRederiveReport]] =
    com.histmersion.api.jobs.JobDetail.Field("eventRederive", None)

  def summary(r: EventRederiveReport): String =
    def counts(cs: List[EventCount]) = cs.map(c => s"${c.key} ${c.n}").mkString(", ")
    List(
      Some(
        s"${r.providers.mkString(", ")}: ${r.records} record(s) re-derived (${r.fromRaw} from the catalogue's own record)"
      ),
      Some(
        s"${r.changed} dating(s) ${if r.dryRun then "would change" else "changed"}, ${r.updated} event(s) refreshed"
      ),
      Option.when(r.after.nonEmpty)(s"precision ${counts(r.before)} → ${counts(r.after)}"),
      Option.when(r.offMap > 0)(s"${r.offMap} imprecise (off the map, on the list)"),
      r.corrections.summary
    ).flatten.mkString(" · ")

  def markdown(r: EventRederiveReport): String =
    val head =
      s"## Event re-derivation — ${r.providers.mkString(", ")}${if r.dryRun then " (dry run: nothing stored)" else ""}"
    (head :: "" :: summary(r).split(" · ").toList.map("- " + _) ++ r.examples.map("  - " + _)).mkString("\n")
