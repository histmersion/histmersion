package com.histmersion
package domain.library

import com.histmersion.api.library.Citekey

// S11 UNCHANGED (import would clash): a local Source next to api.library.Source
final case class Source(id: Long)

object Library:
  // S12 REWRITE: same package, own Source
  def own(s: Source): Long = s.id
  // S13 UNCHANGED: api's Source cannot be imported here (the package has its own Source)
  def other(s: com.histmersion.api.library.Source): Int = 0
  // S14 REWRITE: two Citekeys: the first gets the import, the other keeps its path
  def keys(a: Citekey, b: com.histmersion.Citekey): Int = 0
  // S15 REWRITE: Year from values vs api: values' Year is in scope once the package is chained, api's keeps its path
  def years(a: Year, b: com.histmersion.api.catalogue.Year): Int = 0
