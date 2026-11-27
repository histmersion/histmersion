package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final case class TimeHistogram(
    from: TimeHistogram.From,
    to: TimeHistogram.To,
    bucket: TimeHistogram.Bucket,
    buckets: List[TimeBucket],
    // Defaults keep the `by=time` JSON byte-identical for older clients.
    by: TimeHistogram.By = TimeHistogram.By("time"),
    unit: TimeHistogram.Unit = Histogram.Unit("sources"),
    total: TimeHistogram.Total = TimeHistogram.Total(0),
    depth: TimeHistogram.Depth = TimeHistogram.Depth(0),
    asOf: Option[Year] = None,
    tree: List[Bucket] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object TimeHistogram:
  type From = From.T
  object From extends Newtype[Long, Pure]
  type To = To.T
  object To extends Newtype[Long, Pure]
  type Bucket = Bucket.T
  object Bucket extends Newtype[Long, Pure]
  type By = By.T
  object By extends Newtype[String, Pure]
  export Histogram.Unit
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type Depth = Depth.T
  object Depth extends Newtype[Int, Pure]
