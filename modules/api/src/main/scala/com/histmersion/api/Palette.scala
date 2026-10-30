package com.histmersion.api

object Palette:
  val edgeKinds: List[String] = List(
    "references",
    "challenges",
    "refutes",
    "updates",
    "superseded_by",
    "supersedes",
    "confirms",
    "cites",
    "foundational_for",
    "compilation_of",
    "review_of",
    "verifies"
  )
  val statuses: List[String] =
    List("foundational", "current", "contested", "superseded", "debunked", "retracted", "unassessed")
  val mediaKinds: List[String] = List("figure", "photo", "map", "plan", "scan")
  val confidences: List[String] = List("high", "medium", "low", "contested")

  val goalStages: List[String] = com.histmersion.values.GoalStatus.all

  val rampStops: Int = 5

  private def token(family: String, known: List[String], name: String): String =
    if known.contains(name) then s"--$family-$name" else s"--$family-default"

  def edge(kind: String): String = token("edge", edgeKinds, kind)
  def status(status: String): String = token("status", statuses, status)

  def goalStage(stage: String): String = s"--stage-${stage.trim.toLowerCase}"
  def goalStageSoft(stage: String): String = s"${goalStage(stage)}-soft"
  def media(kind: String): String = token("media", mediaKinds, kind)
  def confidence(level: String): String = s"--conf-$level"

  def allTokens: List[String] =
    (edgeKinds.map(edge) :+ "--edge-default") ++ (statuses.map(status) :+ "--status-default") ++
      (mediaKinds.map(media) :+ "--media-default") ++ confidences.map(confidence) ++
      (0 until rampStops).map(i => s"--ramp-$i") ++ goalStages.flatMap(s => List(goalStage(s), goalStageSoft(s))) ++
      List("--graph-hover", "--graph-label", "--graph-label-hover", "--place-pick", "--place-preview") ++
      mapLabelTokens

  val mapLabelTokens: List[String] =
    for tone <- List("light", "dark"); part <- List("ink", "halo", "note") yield s"--map-label-$part-$tone"
