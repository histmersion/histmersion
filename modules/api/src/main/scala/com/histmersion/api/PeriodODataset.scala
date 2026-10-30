package com.histmersion.api

/** A parsed dump (or seed): the authorities, the seed's curated overlays — `parents` (where a definition sits under
  * another of its authority's), `presets` (which definitions the timeline offers, under which region heading) and
  * `version` (bumped whenever that curation changes) — all empty in a real PeriodO dump, and the definitions that were
  * not imported, with the reason.
  */
final case class PeriodODataset(
    authorities: List[PeriodOAuthority],
    parents: Map[String, String] = Map.empty,
    skipped: List[SkippedPeriod] = Nil,
    presets: Map[String, PresetRegion] = Map.empty,
    version: Option[Int] = None
):
  def size: Int = authorities.map(_.periods.size).sum

  /** A curated seed (it places and offers its periods itself), not a raw PeriodO dump. */
  def curated: Boolean = parents.nonEmpty || presets.nonEmpty

  /** Only the named authorities (all of them when `ids` is empty) — and only THEIR unreadable definitions: a PeriodO
    * period id begins with its authority's id.
    */
  def only(ids: Set[String]): PeriodODataset =
    if ids.isEmpty then this
    else
      copy(
        authorities = authorities.filter(a => ids.contains(a.id)),
        skipped = skipped.filter(s => ids.exists(s.id.startsWith))
      )
