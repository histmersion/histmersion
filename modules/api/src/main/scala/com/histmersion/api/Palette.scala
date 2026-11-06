package com.histmersion.api

object Palette:
  object Ramp:
    type Stops = Stops.T
    object Stops extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CssVar = CssVar.T
  object CssVar extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val edgeKinds: List[com.histmersion.api.library.Edge.Kind.Wire] = List(
    com.histmersion.api.library.Edge.Kind.Wire("references"),
    com.histmersion.api.library.Edge.Kind.Wire("challenges"),
    com.histmersion.api.library.Edge.Kind.Wire("refutes"),
    com.histmersion.api.library.Edge.Kind.Wire("updates"),
    com.histmersion.api.library.Edge.Kind.Wire("superseded_by"),
    com.histmersion.api.library.Edge.Kind.Wire("supersedes"),
    com.histmersion.api.library.Edge.Kind.Wire("confirms"),
    com.histmersion.api.library.Edge.Kind.Wire("cites"),
    com.histmersion.api.library.Edge.Kind.Wire("foundational_for"),
    com.histmersion.api.library.Edge.Kind.Wire("compilation_of"),
    com.histmersion.api.library.Edge.Kind.Wire("review_of"),
    com.histmersion.api.library.Edge.Kind.Wire("verifies")
  )
  val statuses: List[com.histmersion.api.library.Source.Status.Wire] =
    List(com.histmersion.api.library.Source.Status.Wire("foundational"), com.histmersion.api.library.Source.Status.Wire("current"), com.histmersion.api.library.Source.Status.Wire("contested"), com.histmersion.api.library.Source.Status.Wire("superseded"), com.histmersion.api.library.Source.Status.Wire("debunked"), com.histmersion.api.library.Source.Status.Wire("retracted"), com.histmersion.api.library.Source.Status.Wire("unassessed"))
  val mediaKinds: List[com.histmersion.api.media.MediaItem.Kind] = List(com.histmersion.api.media.MediaItem.Kind("figure"), com.histmersion.api.media.MediaItem.Kind("photo"), com.histmersion.api.media.MediaItem.Kind("map"), com.histmersion.api.media.MediaItem.Kind("plan"), com.histmersion.api.media.MediaItem.Kind("scan"))
  val confidences: List[com.histmersion.api.library.Confidence.Wire] = List(com.histmersion.api.library.Confidence.Wire("high"), com.histmersion.api.library.Confidence.Wire("medium"), com.histmersion.api.library.Confidence.Wire("low"), com.histmersion.api.library.Confidence.Wire("contested"))

  val goalStages: List[com.histmersion.api.automation.Goal.Status.All] = com.histmersion.api.automation.Goal.Status.all

  val rampStops: com.histmersion.api.Palette.Ramp.Stops = _root_.com.histmersion.api.Palette.Ramp.Stops(5)

  private def token(family: String, known: List[String], name: String): String =
    if known.contains(name) then (s"--$family-$name") else (s"--$family-default")

  def edge(kind: com.histmersion.api.library.Edge.Kind.Wire): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(token("edge", edgeKinds.map(_.value), kind.value))
  def status(status: com.histmersion.api.library.Source.Status.Wire): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(token("status", statuses.map(_.value), status.value))

  def goalStage(stage: com.histmersion.api.automation.Goal.Status.All): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(s"--stage-${stage.value.trim.toLowerCase}")
  def goalStageSoft(stage: com.histmersion.api.automation.Goal.Status.All): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(s"${goalStage(stage).value}-soft")
  def media(kind: com.histmersion.api.media.MediaItem.Kind): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(token("media", mediaKinds.map(_.value), kind.value))
  def confidence(level: com.histmersion.api.library.Confidence.Wire): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(s"--conf-${level.value}")

  def allTokens: List[com.histmersion.api.Palette.CssVar] =
    (edgeKinds.map(edge) :+ _root_.com.histmersion.api.Palette.CssVar("--edge-default")) ++ (statuses.map(status) :+ _root_.com.histmersion.api.Palette.CssVar("--status-default")) ++
      (mediaKinds.map(media) :+ _root_.com.histmersion.api.Palette.CssVar("--media-default")) ++ confidences.map(confidence) ++
      (0 until rampStops.value).map(i => _root_.com.histmersion.api.Palette.CssVar(s"--ramp-$i")) ++ goalStages.flatMap(s => List(goalStage(s), goalStageSoft(s))) ++
      List("--graph-hover", "--graph-label", "--graph-label-hover", "--place-pick", "--place-preview").map(_root_.com.histmersion.api.Palette.CssVar(_)) ++
      mapLabelTokens

  val mapLabelTokens: List[com.histmersion.api.Palette.CssVar] =
    for tone <- List("light", "dark"); part <- List("ink", "halo", "note") yield _root_.com.histmersion.api.Palette.CssVar(s"--map-label-$part-$tone")
