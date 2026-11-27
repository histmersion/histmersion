package com.histmersion
package config

import io.github.iltotore.iron.constraint.numeric.{Multiple, Positive}

import scala.util.Try
import com.histmersion.lint.{movesTo, FeaturePlace}

/** A memory size of the bundled Postgres, in bytes: a whole number of kilobytes, the smallest unit Postgres reads a
  * size in, so every value is written exactly ([[written]]).
  */
@movesTo(FeaturePlace.UtilDatabase)
type PgBytes = PgBytes.T

object PgBytes extends Newtype[Long, Positive & Multiple[1024L]]:

  /** A unit Postgres reads a size in, named as Postgres spells it (case and all: `mb` is no unit to Postgres), largest
    * first.
    */
  enum Scale(val bytes: Long):
    case TB extends Scale(1L << 40)
    case GB extends Scale(1L << 30)
    case MB extends Scale(1L << 20)
    case kB extends Scale(1L << 10)

  /** `count` of `scale`, when that is a size: more than nothing, and no more than a `Long` holds. */
  def of(count: Long, scale: Scale): Option[PgBytes] =
    Try(Math.multiplyExact(count, scale.bytes)).toOption.flatMap(option)

  /** `count` megabytes (a positive count: the sizes the `auto` rules start from). */
  def megabytes(count: Int): PgBytes = applyUnsafe(math.max(1L, count.toLong) * Scale.MB.bytes)

  /** `ram` divided by `divisor`, in whole megabytes (as `auto` has always rounded), and kept within `least` and `most`.
    */
  def share(ram: ByteCount, divisor: Int, least: PgBytes, most: Option[PgBytes]): PgBytes =
    val whole = math.max(1L, ram.wholeMegabytes / divisor) * Scale.MB.bytes
    val bounded = most.fold(whole)(m => math.min(m.value, whole))
    applyUnsafe(math.max(least.value, bounded))

  extension (b: PgBytes)
    /** As Postgres reads it: in the largest unit that holds it whole (`2GB`, `1536MB`, `64kB`). */
    def written: PgSize.Written =
      val scale = Scale.values.find(s => b.value % s.bytes == 0).getOrElse(Scale.kB)
      PgSize.Written.of(b.value / scale.bytes, scale)
