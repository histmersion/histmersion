package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class TimeHistogram(
    from: TimeHistogram.From,
    to: TimeHistogram.To,
    bucket: TimeHistogram.Bucket,
    buckets: List[TimeBucket],
    // Defaults keep the `by=time` JSON byte-identical for older clients.
    by: TimeHistogram.By = _root_.com.histmersion.api.library.TimeHistogram.By("time"),
    unit: TimeHistogram.Unit = _root_.com.histmersion.api.library.Histogram.Unit("sources"),
    total: TimeHistogram.Total = _root_.com.histmersion.api.library.TimeHistogram.Total(0),
    depth: TimeHistogram.Depth = _root_.com.histmersion.api.library.TimeHistogram.Depth(0),
    asOf: Option[com.histmersion.api.catalogue.Year] = None,
    tree: List[Bucket] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object TimeHistogram:
  type From = From.T
  object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type To = To.T
  object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bucket = Bucket.T
  object Bucket extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type By = By.T
  object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unit = com.histmersion.api.library.Histogram.Unit
  val Unit: _root_.com.histmersion.api.library.Histogram.Unit.type = _root_.com.histmersion.api.library.Histogram.Unit
  type Total = Total.T
  object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Depth = Depth.T
  object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
