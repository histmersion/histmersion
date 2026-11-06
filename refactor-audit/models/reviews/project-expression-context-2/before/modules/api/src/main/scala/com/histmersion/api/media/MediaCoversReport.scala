package com.histmersion.api.media
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MediaCoversReport(
    dryRun: com.histmersion.api.media.MediaImportRequest.DryRun,
    sources: MediaCoversReport.Sources = _root_.com.histmersion.api.media.MediaCoversReport.Sources(0),
    covers: MediaCoversReport.Covers = _root_.com.histmersion.api.media.MediaCoversReport.Covers(0),
    illustrations: MediaCoversReport.Illustrations = _root_.com.histmersion.api.media.MediaCoversReport.Illustrations(0),
    lines: List[MediaSourceLine] = Nil,
    skipped: List[MediaCount] = Nil,
    failed: List[MediaCoversReport.Failed] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MediaCoversReport:


  type Sources = Sources.T
  object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Covers = Covers.T
  object Covers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Illustrations = Illustrations.T
  object Illustrations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failed = Failed.T
  object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def combine(a: MediaCoversReport, b: MediaCoversReport): MediaCoversReport =
    MediaCoversReport(
      ((a.dryRun) == _root_.com.histmersion.api.media.MediaImportRequest.DryRun(true)) && ((b.dryRun) == _root_.com.histmersion.api.media.MediaImportRequest.DryRun(true)),
      _root_.com.histmersion.api.media.MediaCoversReport.Sources((a.sources).value + (b.sources).value),
      _root_.com.histmersion.api.media.MediaCoversReport.Covers((a.covers).value + (b.covers).value),
      _root_.com.histmersion.api.media.MediaCoversReport.Illustrations((a.illustrations).value + (b.illustrations).value),
      a.lines ++ b.lines,
      (a.skipped ++ b.skipped)
        .groupBy(_.key)
        .toList
        .map((k, cs) => MediaCount(k, cs.map(_.n).sum))
        .sortBy(c => (-c.n, c.key)),
      a.failed ++ b.failed
    )
