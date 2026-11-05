package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaCoversReport(
    dryRun: Boolean,
    sources: Int = 0,
    covers: Int = 0,
    illustrations: Int = 0,
    lines: List[MediaSourceLine] = Nil,
    skipped: List[MediaCount] = Nil,
    failed: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaCoversReport:


  def combine(a: MediaCoversReport, b: MediaCoversReport): MediaCoversReport =
    MediaCoversReport(
      a.dryRun && b.dryRun,
      a.sources + b.sources,
      a.covers + b.covers,
      a.illustrations + b.illustrations,
      a.lines ++ b.lines,
      (a.skipped ++ b.skipped)
        .groupBy(_.key)
        .toList
        .map((k, cs) => MediaCount(k, cs.map(_.n).sum))
        .sortBy(c => (-c.n, c.key)),
      a.failed ++ b.failed
    )
