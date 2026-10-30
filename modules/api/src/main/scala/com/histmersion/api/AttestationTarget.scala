package com.histmersion.api

object AttestationTarget:
  val Finding = "finding"
  val Event = "event"
  val EventDating = "event_dating"
  val Place = "place"
  val Edge = "edge"
  val all: List[String] = List(Finding, Event, EventDating, Place, Edge)

  /** Pure: a target type a write may name (trimmed, any case), or why not — checked before the insert, so an unknown
    * one is a 400 naming the five, never the table's check constraint (a 500, #3770).
    */
  def check(t: String): Either[String, String] =
    val n = Option(t).getOrElse("").trim.toLowerCase
    Either.cond(all.contains(n), n, s"unknown target type '$t' (${all.mkString(", ")})")
