package com.histmersion.cli

import scala.jdk.CollectionConverters.*

/** Where `calibre import` writes, once the citekey is known (#714). */
enum ImportTarget:
  /** No record has this citekey yet. */
  case Create

  /** The record exists; enrich it and link it to the imported entry. `previous` is the Calibre id it was linked to
    * before, when that was a different one.
    */
  case Update(previous: Option[ImportTarget.Update.Previous])

object ImportTarget:
  object Update:
    type Previous = Previous.T
    object Previous extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
