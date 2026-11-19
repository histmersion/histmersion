package com.histmersion
package server.repo

final case class Source(id: Long)

object Repo:
  // P9 UNCHANGED: no package head inside the api under server/ (packageHeadExcept), and api's Source cannot be imported
  def other(s: com.histmersion.api.library.Source): Int = s.id
  // P10 REWRITE to Source: the own Source is unaffected
  def own(s: Source): Long = s.id
