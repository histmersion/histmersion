package com.histmersion
package cli

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** Where `calibre import` writes, once the citekey is known (#714). */
enum ImportTarget:
  /** No record has this citekey yet. */
  case Create

  /** The record exists; enrich it and link it to the imported entry. `previous` is the Calibre id it was linked to
    * before, when that was a different one.
    */
  case Update(previous: Option[ImportTarget.Update.Previous])

object ImportTarget:
  enum Explicit derives CanEqual:
    case Asked, Generated
  object Explicit:
    extension (explicit: Explicit) def asked: Boolean = explicit == Asked
  object Update:
    type Previous = Previous.T
    object Previous extends Newtype[String, Pure]
