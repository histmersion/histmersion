package com.histmersion.api.exchange
import com.histmersion.api.given
object BundleEventSets:
  def candidateStatus(here: String, there: String): String =
    if here == "proposed" then there else here

  /** Timestamps share one fixed-width UTC format, so text order is time order. */
  def newer(here: Option[String], there: Option[String]): Boolean = (here, there) match
    case (None, Some(_))    => true
    case (Some(h), Some(t)) => t > h
    case _                  => false
