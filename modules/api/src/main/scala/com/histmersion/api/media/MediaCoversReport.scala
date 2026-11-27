package com.histmersion
package api.media
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.RepairAcquire
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class MediaCoversReport(
    dryRun: RepairAcquire.RepairRequest.DryRun,
    sources: MediaCoversReport.Sources = MediaCoversReport.Sources(0),
    covers: MediaCoversReport.Covers = MediaCoversReport.Covers(0),
    illustrations: MediaCoversReport.Illustrations = MediaCoversReport.Illustrations(0),
    lines: List[MediaSourceLine] = Nil,
    skipped: List[MediaCount] = Nil,
    failed: List[MediaCoversReport.Failed] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MediaCoversReport:

  type Sources = Sources.T
  object Sources extends Newtype[Int, Pure]
  type Covers = Covers.T
  object Covers extends Newtype[Int, Pure]
  type Illustrations = Illustrations.T
  object Illustrations extends Newtype[Int, Pure]
  type Failed = Failed.T
  object Failed extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def combine(a: MediaCoversReport, b: MediaCoversReport): MediaCoversReport =
    MediaCoversReport(
      RepairAcquire.RepairRequest.DryRun(
        ((a.dryRun) == RepairAcquire.RepairRequest.DryRun(true)) && ((b.dryRun) == RepairAcquire.RepairRequest
          .DryRun(true))
      ),
      Sources((a.sources).value + (b.sources).value),
      Covers((a.covers).value + (b.covers).value),
      Illustrations((a.illustrations).value + (b.illustrations).value),
      a.lines ++ b.lines,
      (a.skipped ++ b.skipped)
        .groupBy(_.key)
        .toList
        .map((k, cs) => MediaCount(k, MediaCount.N(cs.map(c => c.n.value: Int).sum)))
        .sortBy(c => (-(c.n).value, c.key)),
      a.failed ++ b.failed
    )
