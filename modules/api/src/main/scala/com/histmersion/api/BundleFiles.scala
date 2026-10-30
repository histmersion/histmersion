package com.histmersion.api

import com.histmersion.values.BundleKind

/** The files of a bundle besides the blobs, in the order they are written. The entity files of [[BundleKind]] come
  * first; the owned sets that have no uid of their own follow.
  */
object BundleFiles:
  val Edges = "entities/edges.ndjson"
  val UnresolvedEdges = "entities/unresolved-edges.ndjson"
  val Assessments = "entities/assessments.ndjson"
  val Aliases = "entities/aliases.ndjson"

  /** Old article slugs (`article_aliases`), as old citekeys are [[Aliases]]. */
  val ArticleAliases = "entities/article-aliases.ndjson"

  /** Old event ids, goal slugs (V053) and place ids (V055) — the same rule as an old article slug. */
  val EventAliases = "entities/event-aliases.ndjson"
  val GoalAliases = "entities/goal-aliases.ndjson"
  val PlaceAliases = "entities/place-aliases.ndjson"

  /** The event catalogues' records, the event-link candidates and checks, the corrections by hand and the site types
    * (V085, V088; #1647) — absent from an older bundle, which reads as empty.
    */
  val EventCatalogue = "entities/event-catalogue.ndjson"
  val EventCandidates = "entities/event-candidates.ndjson"
  val EventChecks = "entities/event-checks.ndjson"
  val Corrections = "entities/corrections.ndjson"
  val SiteTypes = "entities/site-types.ndjson"
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
        Corrections,
        SiteTypes,
        Tombstones
      )
