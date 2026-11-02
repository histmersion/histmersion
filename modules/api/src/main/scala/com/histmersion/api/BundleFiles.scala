package com.histmersion.api

import com.histmersion.values.BundleKind

object BundleFiles:
  val Edges = "entities/edges.ndjson"
  val UnresolvedEdges = "entities/unresolved-edges.ndjson"
  val Assessments = "entities/assessments.ndjson"
  val Aliases = "entities/aliases.ndjson"

  val ArticleAliases = "entities/article-aliases.ndjson"

  val EventAliases = "entities/event-aliases.ndjson"
  val GoalAliases = "entities/goal-aliases.ndjson"
  val PlaceAliases = "entities/place-aliases.ndjson"

  /** Absent from an older bundle, which reads as empty. */
  val EventCatalogue = "entities/event-catalogue.ndjson"
  val EventCandidates = "entities/event-candidates.ndjson"
  val EventChecks = "entities/event-checks.ndjson"
  val EventCorrections = "entities/corrections.ndjson"
  val PlaceSiteTypes = "entities/site-types.ndjson"
  val Tombstones = "entities/tombstones.ndjson"

  val all: List[String] =
    BundleKind.values.toList.map(_.file) ++
      List(
        Edges,
        UnresolvedEdges,
        Assessments,
        Aliases,
        ArticleAliases,
        EventAliases,
        GoalAliases,
        PlaceAliases,
        EventCatalogue,
        EventCandidates,
        EventChecks,
        EventCorrections,
        PlaceSiteTypes,
        Tombstones
      )
