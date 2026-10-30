package com.histmersion.api

object AttestationTarget:
  val Finding = "finding"
  val Event = "event"
  val EventDating = "event_dating"
  val Place = "place"
  val Edge = "edge"
  val all: List[String] = List(Finding, Event, EventDating, Place, Edge)

  def check(t: String): Either[String, String] =
    val n = Option(t).getOrElse("").trim.toLowerCase
    Either.cond(all.contains(n), n, s"unknown target type '$t' (${all.mkString(", ")})")
