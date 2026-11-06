package com.histmersion.api

object Palette:
  object Ramp:
    type Stops = Stops.T
    object Stops extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CssVar = CssVar.T
  object CssVar extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalStageSoftStage = _root_.com.histmersion.api.Goal.Status.Wire
  val GoalStageSoftStage: _root_.com.histmersion.api.Goal.Status.Wire.type = _root_.com.histmersion.api.Goal.Status.Wire

  val edgeKinds: List[com.histmersion.api.Edge.Kind.Wire] = List(
    _root_.com.histmersion.api.Edge.Kind.Wire("references"),
    _root_.com.histmersion.api.Edge.Kind.Wire("challenges"),
    _root_.com.histmersion.api.Edge.Kind.Wire("refutes"),
    _root_.com.histmersion.api.Edge.Kind.Wire("updates"),
    _root_.com.histmersion.api.Edge.Kind.Wire("superseded_by"),
    _root_.com.histmersion.api.Edge.Kind.Wire("supersedes"),
    _root_.com.histmersion.api.Edge.Kind.Wire("confirms"),
    _root_.com.histmersion.api.Edge.Kind.Wire("cites"),
    _root_.com.histmersion.api.Edge.Kind.Wire("foundational_for"),
    _root_.com.histmersion.api.Edge.Kind.Wire("compilation_of"),
    _root_.com.histmersion.api.Edge.Kind.Wire("review_of"),
    _root_.com.histmersion.api.Edge.Kind.Wire("verifies")
  )
  val statuses: List[com.histmersion.api.Source.Status.Wire] =
    List(_root_.com.histmersion.api.Source.Status.Wire("foundational"), _root_.com.histmersion.api.Source.Status.Wire("current"), _root_.com.histmersion.api.Source.Status.Wire("contested"), _root_.com.histmersion.api.Source.Status.Wire("superseded"), _root_.com.histmersion.api.Source.Status.Wire("debunked"), _root_.com.histmersion.api.Source.Status.Wire("retracted"), _root_.com.histmersion.api.Source.Status.Wire("unassessed"))
  val mediaKinds: List[com.histmersion.api.Media.Kind.Wire] = List(_root_.com.histmersion.api.Media.Kind.Wire("figure"), _root_.com.histmersion.api.Media.Kind.Wire("photo"), _root_.com.histmersion.api.Media.Kind.Wire("map"), _root_.com.histmersion.api.Media.Kind.Wire("plan"), _root_.com.histmersion.api.Media.Kind.Wire("scan"))
  val confidences: List[com.histmersion.api.Confidence.Wire] = List(_root_.com.histmersion.api.Confidence.Wire("high"), _root_.com.histmersion.api.Confidence.Wire("medium"), _root_.com.histmersion.api.Confidence.Wire("low"), _root_.com.histmersion.api.Confidence.Wire("contested"))

  val goalStages: List[com.histmersion.api.Goal.Status.Wire] = com.histmersion.api.automation.Goal.Status.all

  val rampStops: com.histmersion.api.Palette.Ramp.Stops = _root_.com.histmersion.api.Palette.Ramp.Stops(5)

  private def token(family: String, known: List[String], name: String): String =
    if known.contains(name) then (s"--$family-$name") else (s"--$family-default")

  def edge(kind: com.histmersion.api.Edge.Kind.Wire): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar((token(("edge"), (edgeKinds).map(refactorValue2 => ((refactorValue2).value)), ((kind).value))).value)
  def status(status: com.histmersion.api.Source.Status.Wire): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar((token(("status"), (statuses).map(refactorValue3 => ((refactorValue3).value)), ((status).value))).value)

  def goalStage(stage: com.histmersion.api.Goal.Status.Wire): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(s"--stage-${(stage).value.trim.toLowerCase}")
  def goalStageSoft(stage: com.histmersion.api.Palette.GoalStageSoftStage): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(s"${goalStage(stage)}-soft")
  def media(kind: com.histmersion.api.Media.Kind.Wire): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar((token(("media"), (mediaKinds).map(refactorValue9 => ((refactorValue9).value)), ((kind).value))).value)
  def confidence(level: com.histmersion.api.Confidence.Wire): com.histmersion.api.Palette.CssVar = _root_.com.histmersion.api.Palette.CssVar(s"--conf-$level")

  def allTokens: List[com.histmersion.api.Palette.CssVar] =
    (edgeKinds.map({ val refactorFunction2 = edge; (refactorValue10) => refactorFunction2(_root_.com.histmersion.api.Edge.Kind.Wire((refactorValue10).value)) }) :+ "--edge-default") ++ (statuses.map({ val refactorFunction = status; (refactorValue4) => refactorFunction(_root_.com.histmersion.api.Source.Status.Wire((refactorValue4).value)) }) :+ "--status-default") ++
      (mediaKinds.map({ val refactorFunction3 = media; (refactorValue5) => refactorFunction3(_root_.com.histmersion.api.Media.Kind.Wire((refactorValue5).value)) }) :+ "--media-default") ++ confidences.map({ val refactorFunction4 = confidence; (refactorValue6) => refactorFunction4(_root_.com.histmersion.api.Confidence.Wire((refactorValue6).value)) }) ++
      (0 until (rampStops).value).map(i => s"--ramp-$i") ++ goalStages.flatMap(s => List(goalStage(_root_.com.histmersion.api.Goal.Status.Wire((s).value)), goalStageSoft(_root_.com.histmersion.api.Goal.Status.Wire((s).value)))) ++
      List("--graph-hover", "--graph-label", "--graph-label-hover", "--place-pick", "--place-preview") ++
      mapLabelTokens

  val mapLabelTokens: List[com.histmersion.api.Palette.CssVar] =
    for tone <- List("light", "dark"); part <- List("ink", "halo", "note") yield _root_.com.histmersion.api.Palette.CssVar(s"--map-label-$part-$tone")
