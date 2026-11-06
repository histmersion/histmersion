package com.histmersion.domain.catalogue.places

import com.histmersion.domain.*

import com.histmersion.api.Plural
import com.histmersion.api.checks.Lint
import com.histmersion.api.given

import com.histmersion.api.checks.Lint.{LintItem, LintRuleReport, Rule}

/** `lint --rule place-imported-point-area` (2026-10-09): the GeoNames and Wikidata places of an area kind (region,
  * polity, people — `places.sources.point-area-kinds`) that an import stored with only a point, before the importer
  * stopped keeping them ([[Gazetteer.dropsPointArea]]). The listing is what the user decides `--fix` on: the counts by
  * source, kind and dating, then EVERY such place that holds a finding (and how many of those findings it would leave
  * with no place at all), then a sample of the rest. Pure: the rows come from `db.PointAreaRepo`.
  */
object PointAreas:

  /** How a place's period reads: `dated` (a known start), `end-only` (an end, the start unknown —
    * [[PlaceDating.EndOnly]]) or `undated` (an open range: the source said nothing).
    */
  type ReportMaxItems = ReportMaxItems.T
  object ReportMaxItems extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixedResult = FixedResult.T
  object FixedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixedRemoved = FixedRemoved.T
  object FixedRemoved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixedRemaining = FixedRemaining.T
  object FixedRemaining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixedFindings = FixedFindings.T
  object FixedFindings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixedSources = FixedSources.T
  object FixedSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Dating:
    case Dated, EndOnly, Undated

    def wire: com.histmersion.domain.catalogue.places.PointAreas.Dating.WireResult = this match
      case Dated   => com.histmersion.domain.catalogue.places.PointAreas.Dating.WireResult("dated")
      case EndOnly => com.histmersion.domain.catalogue.places.PointAreas.Dating.WireResult("end-only")
      case Undated => com.histmersion.domain.catalogue.places.PointAreas.Dating.WireResult("undated")

  object Dating:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfStartUnknown = OfStartUnknown.T
    object OfStartUnknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfLowerInf = OfLowerInf.T
    object OfLowerInf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfUpperInf = OfUpperInf.T
    object OfUpperInf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(startUnknown: com.histmersion.domain.catalogue.places.PointAreas.Dating.OfStartUnknown, lowerInf: com.histmersion.domain.catalogue.places.PointAreas.Dating.OfLowerInf, upperInf: com.histmersion.domain.catalogue.places.PointAreas.Dating.OfUpperInf): Dating =
      if startUnknown then EndOnly
      else if lowerInf && upperInf then Undated
      else Dated

  /** The places of one source, kind and dating. */
  final case class Group(source: Group.Source, kind: Group.Kind, dating: Dating, places: Group.Places)

  object Group:
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A listed place. `findings` are the findings matched to it (`finding_places`); `alone` how many of them it is the
    * ONLY place of — those would be left unplaced by a removal; `pinned` the attestations, asset links and place
    * overrides (`places set`, V080) that name it, which keep it out of `--fix` and out of the importer's prune: each is
    * a person's decision about the place.
    */
  final case class Listed(
      id: Listed.Id,
      label: Listed.Label,
      source: Listed.Source,
      kind: Listed.Kind,
      dating: Dating,
      findings: Listed.Findings = com.histmersion.domain.catalogue.places.PointAreas.Listed.Findings(0),
      alone: Listed.Alone = com.histmersion.domain.catalogue.places.PointAreas.Listed.Alone(0),
      pinned: Listed.Pinned = com.histmersion.domain.catalogue.places.PointAreas.Listed.Pinned(0)
  ):
    def detail: com.histmersion.domain.catalogue.places.PointAreas.Listed.DetailResult =
      val what = s"$label ($source $kind, ${dating.wire})"
      val held =
        if findings == 0 then ""
        else if alone == 0 then
          s": ${Plural.count(findings.toLong, com.histmersion.api.Plural.CountSingular("finding"))}, ${
              if findings == 1 then "also" else "each also"
            } at another place"
        else if findings == 1 then ": 1 finding, at no other place"
        else s": ${Plural.count(findings.toLong, com.histmersion.api.Plural.CountSingular("finding"))}, $alone of them at no other place"
      val kept = if pinned > 0 then
        s" — kept: named by $pinned attestation(s)/asset link(s)/place override(s); move or reset those first"
      else ""
      what + held + kept

  object Listed:
    type DetailResult = DetailResult.T
    object DetailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Alone = Alone.T
    object Alone extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pinned = Pinned.T
    object Pinned extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The rule's report. `count` is the true number of places; the items are the group lines (always), every place
    * holding a finding or pinned by evidence (always — they are what a person must see before a removal), then `sample`
    * up to `maxItems` items in all.
    */
  def report(
      rule: Rule,
      groups: List[Group],
      holders: List[Listed],
      sample: List[Listed],
      maxItems: com.histmersion.domain.catalogue.places.PointAreas.ReportMaxItems
  ): LintRuleReport =
    val total = groups.map(_.places).sum
    if total == 0 then LintRuleReport(rule.name, rule.description, com.histmersion.api.checks.Lint.LintRuleReport.Count(0), Nil)
    else
      val groupItems = groups
        .sortBy(g => (g.source, g.kind, g.dating.ordinal))
        .map(g => LintItem(s"${g.source} · ${g.kind} · ${g.dating.wire}", Plural.count(g.places.toLong, "place")))
      val must = holders.sortBy(h => (-h.findings, h.id))
      val seen = must.map(_.id).toSet
      val room = math.max(0, maxItems - groupItems.size - must.size)
      val rest = sample.filterNot(l => seen.contains(l.id)).take(room)
      LintRuleReport(
        rule.name,
        rule.description,
        total,
        groupItems ++ must.map(h => LintItem(h.id, h.detail)) ++ rest.map(l => LintItem(l.id, l.detail))
      )

  /** One line for `lint --fix`. */
  def fixed(removed: com.histmersion.domain.catalogue.places.PointAreas.FixedRemoved, remaining: com.histmersion.domain.catalogue.places.PointAreas.FixedRemaining, findings: com.histmersion.domain.catalogue.places.PointAreas.FixedFindings, sources: com.histmersion.domain.catalogue.places.PointAreas.FixedSources): com.histmersion.domain.catalogue.places.PointAreas.FixedResult =
    s"${Lint.PlaceImportedPointArea}: $removed point-only imported area(s) removed" +
      (if findings > 0 then
         s", ${Plural.count(findings.toLong, com.histmersion.api.Plural.CountSingular("finding"))} of ${Plural.count(sources.toLong, com.histmersion.api.Plural.CountSingular("source"))} matched to places again"
       else "") +
      (if remaining > 0 then s", $remaining still to remove" else "")
