package com.histmersion.api

object Palette:
  type EdgeKinds = EdgeKinds.T
  object EdgeKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Statuses = Statuses.T
  object Statuses extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaKinds = MediaKinds.T
  object MediaKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Confidences = Confidences.T
  object Confidences extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalStages = GoalStages.T
  object GoalStages extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RampStops = RampStops.T
  object RampStops extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokenResult = TokenResult.T
  object TokenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokenFamily = TokenFamily.T
  object TokenFamily extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokenKnown = TokenKnown.T
  object TokenKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokenName = TokenName.T
  object TokenName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgeResult = EdgeResult.T
  object EdgeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgeKind = EdgeKind.T
  object EdgeKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusResult = StatusResult.T
  object StatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusStatus = StatusStatus.T
  object StatusStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalStageResult = GoalStageResult.T
  object GoalStageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalStageStage = GoalStageStage.T
  object GoalStageStage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalStageSoftResult = GoalStageSoftResult.T
  object GoalStageSoftResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalStageSoftStage = _root_.com.histmersion.api.Palette.GoalStageStage
  val GoalStageSoftStage: _root_.com.histmersion.api.Palette.GoalStageStage.type = _root_.com.histmersion.api.Palette.GoalStageStage
  type MediaResult = MediaResult.T
  object MediaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaKind = MediaKind.T
  object MediaKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfidenceResult = ConfidenceResult.T
  object ConfidenceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConfidenceLevel = ConfidenceLevel.T
  object ConfidenceLevel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllTokensResult = AllTokensResult.T
  object AllTokensResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapLabelTokens = MapLabelTokens.T
  object MapLabelTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val edgeKinds: List[com.histmersion.api.Palette.EdgeKinds] = List(
    _root_.com.histmersion.api.Palette.EdgeKinds("references"),
    _root_.com.histmersion.api.Palette.EdgeKinds("challenges"),
    _root_.com.histmersion.api.Palette.EdgeKinds("refutes"),
    _root_.com.histmersion.api.Palette.EdgeKinds("updates"),
    _root_.com.histmersion.api.Palette.EdgeKinds("superseded_by"),
    _root_.com.histmersion.api.Palette.EdgeKinds("supersedes"),
    _root_.com.histmersion.api.Palette.EdgeKinds("confirms"),
    _root_.com.histmersion.api.Palette.EdgeKinds("cites"),
    _root_.com.histmersion.api.Palette.EdgeKinds("foundational_for"),
    _root_.com.histmersion.api.Palette.EdgeKinds("compilation_of"),
    _root_.com.histmersion.api.Palette.EdgeKinds("review_of"),
    _root_.com.histmersion.api.Palette.EdgeKinds("verifies")
  )
  val statuses: List[com.histmersion.api.Palette.Statuses] =
    List(_root_.com.histmersion.api.Palette.Statuses("foundational"), _root_.com.histmersion.api.Palette.Statuses("current"), _root_.com.histmersion.api.Palette.Statuses("contested"), _root_.com.histmersion.api.Palette.Statuses("superseded"), _root_.com.histmersion.api.Palette.Statuses("debunked"), _root_.com.histmersion.api.Palette.Statuses("retracted"), _root_.com.histmersion.api.Palette.Statuses("unassessed"))
  val mediaKinds: List[com.histmersion.api.Palette.MediaKinds] = List(_root_.com.histmersion.api.Palette.MediaKinds("figure"), _root_.com.histmersion.api.Palette.MediaKinds("photo"), _root_.com.histmersion.api.Palette.MediaKinds("map"), _root_.com.histmersion.api.Palette.MediaKinds("plan"), _root_.com.histmersion.api.Palette.MediaKinds("scan"))
  val confidences: List[com.histmersion.api.Palette.Confidences] = List(_root_.com.histmersion.api.Palette.Confidences("high"), _root_.com.histmersion.api.Palette.Confidences("medium"), _root_.com.histmersion.api.Palette.Confidences("low"), _root_.com.histmersion.api.Palette.Confidences("contested"))

  val goalStages: List[com.histmersion.api.Palette.GoalStages] = com.histmersion.api.automation.Goal.Status.all

  val rampStops: com.histmersion.api.Palette.RampStops = _root_.com.histmersion.api.Palette.RampStops(5)

  private def token(family: com.histmersion.api.Palette.TokenFamily, known: List[com.histmersion.api.Palette.TokenKnown], name: com.histmersion.api.Palette.TokenName): com.histmersion.api.Palette.TokenResult =
    if known.contains(name) then _root_.com.histmersion.api.Palette.TokenResult(s"--$family-$name") else _root_.com.histmersion.api.Palette.TokenResult(s"--$family-default")

  def edge(kind: com.histmersion.api.Palette.EdgeKind): com.histmersion.api.Palette.EdgeResult = token(_root_.com.histmersion.api.Palette.TokenFamily("edge"), edgeKinds, kind)
  def status(status: com.histmersion.api.Palette.StatusStatus): com.histmersion.api.Palette.StatusResult = token(_root_.com.histmersion.api.Palette.TokenFamily("status"), statuses, status)

  def goalStage(stage: com.histmersion.api.Palette.GoalStageStage): com.histmersion.api.Palette.GoalStageResult = _root_.com.histmersion.api.Palette.GoalStageResult(s"--stage-${(stage).value.trim.toLowerCase}")
  def goalStageSoft(stage: com.histmersion.api.Palette.GoalStageSoftStage): com.histmersion.api.Palette.GoalStageSoftResult = s"${goalStage(stage)}-soft"
  def media(kind: com.histmersion.api.Palette.MediaKind): com.histmersion.api.Palette.MediaResult = token(_root_.com.histmersion.api.Palette.TokenFamily("media"), mediaKinds, kind)
  def confidence(level: com.histmersion.api.Palette.ConfidenceLevel): com.histmersion.api.Palette.ConfidenceResult = _root_.com.histmersion.api.Palette.ConfidenceResult(s"--conf-$level")

  def allTokens: List[com.histmersion.api.Palette.AllTokensResult] =
    (edgeKinds.map(edge) :+ "--edge-default") ++ (statuses.map(status) :+ "--status-default") ++
      (mediaKinds.map(media) :+ "--media-default") ++ confidences.map(confidence) ++
      (0 until rampStops).map(i => s"--ramp-$i") ++ goalStages.flatMap(s => List(goalStage(s), goalStageSoft(s))) ++
      List("--graph-hover", "--graph-label", "--graph-label-hover", "--place-pick", "--place-preview") ++
      mapLabelTokens

  val mapLabelTokens: List[com.histmersion.api.Palette.MapLabelTokens] =
    for tone <- List("light", "dark"); part <- List("ink", "halo", "note") yield _root_.com.histmersion.api.Palette.MapLabelTokens(s"--map-label-$part-$tone")
