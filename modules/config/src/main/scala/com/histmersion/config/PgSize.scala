package com.histmersion
package config

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** One memory setting of the bundled Postgres (`runtime.postgres.*`): a size, `auto` (sized from the machine's RAM by
  * the parameter's own rule, [[PgParameter.auto]]) or nothing (Postgres' own default: the setting is not passed).
  */
@movesTo(FeaturePlace.UtilDatabase)
enum PgSize derives CanEqual:
  case Auto
  case Unset
  case Exactly(bytes: PgBytes)

  /** The size Postgres is started with for `parameter` on a machine with `ram`: none when the setting is unset, or
    * `auto` while the machine's memory is unknown.
    */
  def on(parameter: PgParameter, ram: Option[ByteCount]): Option[PgBytes] = this match
    case Auto           => ram.map(parameter.auto)
    case Unset          => None
    case Exactly(bytes) => Some(bytes)

object PgSize:

  /** A size as the settings write it, and as Postgres reads it: `2GB`, `512MB`, `auto`, `""`. */
  type Written = Written.T
  object Written extends Newtype[String, Pure]:
    /** A whole number and a unit, with or without a space between them (`512MB`, `4 MB`). */
    private val Size = "([0-9]+)\\s*([A-Za-z]+)".r

    /** `count` of `scale`, as Postgres reads it (`2GB`). */
    def of(count: Long, scale: PgBytes.Scale): Written = Written(s"$count${scale.productPrefix}")

    extension (w: Written)
      /** The size these words write, when they write one. A bare number is none: Postgres would read it in the
        * parameter's own unit (8 kB pages for shared buffers, kilobytes for work memory), so the unit is asked for.
        */
      def size: Option[PgSize] = w.value.trim match
        case ""                              => Some(Unset)
        case a if a.equalsIgnoreCase("auto") => Some(Auto)
        case Size(digits, unit)              =>
          for
            count <- digits.toLongOption
            scale <- PgBytes.Scale.values.find(_.productPrefix == unit)
            bytes <- PgBytes.of(count, scale)
          yield Exactly(bytes)
        case _ => None
