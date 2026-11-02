package com.histmersion.api.catalogue
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given

final case class SiteTypesReport(
    dryRun: Boolean,
    pending: Int = 0,
    asked: Int = 0,
    typed: List[EventCount] = Nil,
    untyped: Int = 0,
    requests: Int = 0,
    failed: List[String] = Nil
) derives hearth.kindlings.circederivation.KindlingsCodecAsObject,
      KindlingsSchema
object SiteTypesReport:
  given sttp.tapir.Schema[SiteTypesReport] = summon[KindlingsSchema[SiteTypesReport]].schema
  def summary(r: SiteTypesReport): String =
    if r.dryRun then s"${r.pending} Wikidata site(s) on the map would be asked about"
    else
      (List(
        s"${r.asked} site(s) asked",
        s"typed: ${if r.typed.isEmpty then "none" else r.typed.map(c => s"${c.key} ${c.n}").mkString(", ")}",
        s"${r.untyped} kept the import's class",
        s"${r.pending} left",
        s"${r.requests} request(s)"
      ) ++
        Option.when(r.failed.nonEmpty)(s"${r.failed.size} failed: ${r.failed.take(3).mkString("; ")}")).mkString(" · ")
