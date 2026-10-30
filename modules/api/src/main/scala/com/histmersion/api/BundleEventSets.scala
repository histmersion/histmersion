package com.histmersion.api

/** The rules an import merges the event-catalogue sets by (#1647). Pure. */
object BundleEventSets:
  /** A candidate's status after an import: a decision here stays; a proposal here takes the bundle's. */
  def candidateStatus(here: String, there: String): String =
    if here == "proposed" then there else here

  /** Whether the bundle's version of a timestamped row replaces the one here: only when it is newer (timestamps share
    * one fixed-width UTC format, so text order is time order).
    */
  def newer(here: Option[String], there: Option[String]): Boolean = (here, there) match
    case (None, Some(_))    => true
    case (Some(h), Some(t)) => t > h
    case _                  => false
