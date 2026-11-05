package com.histmersion.cli

import scala.jdk.CollectionConverters.*

/** Where `calibre import` writes, once the citekey is known (#714). */
enum ImportTarget:
  /** No record has this citekey yet. */
  case Create

  /** The record exists; enrich it and link it to the imported entry. `previous` is the Calibre id it was linked to
    * before, when that was a different one.
    */
  case Update(previous: Option[String])
