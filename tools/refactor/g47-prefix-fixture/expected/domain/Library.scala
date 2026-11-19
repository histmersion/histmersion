package com.histmersion
package domain.library

// P0 UNCHANGED: a local Source next to api.library.Source
final case class Source(id: Long)

object Library:
  // P1 REWRITE to Source: same package, own Source
  def own(s: Source): Long = s.id
  // P2 REWRITE to api.library.Source: api's Source cannot be imported here; `api` is in scope once the package is chained
  def other(s: api.library.Source): Int = s.id
  // P3 REWRITE to api.library.Source: rooted at _root_
  def rooted(s: api.library.Source): Int = s.id
  // P4 UNCHANGED: a parameter named api shadows the package
  def shadowed(api: Int, s: com.histmersion.api.library.Source): Int = api + s.id
