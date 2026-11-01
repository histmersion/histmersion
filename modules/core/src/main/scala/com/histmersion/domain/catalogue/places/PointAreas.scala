package com.histmersion.domain.catalogue.places

import com.histmersion.domain.*

import com.histmersion.api.{Lint, Plural}
import com.histmersion.api.given

import com.histmersion.api.Lint.{LintItem, LintRuleReport, Rule}

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
  enum Dating:
    case Dated, EndOnly, Undated

    def wire: String = this match
      case Dated   => "dated"
      case EndOnly => "end-only"
      case Undated => "undated"

  object Dating:
    def of(startUnknown: Boolean, lowerInf: Boolean, upperInf: Boolean): Dating =
      if startUnknown then EndOnly
      else if lowerInf && upperInf then Undated
      else Dated

  /** The places of one source, kind and dating. */
  final case class Group(source: String, kind: String, dating: Dating, places: Int)

  /** A listed place. `findings` are the findings matched to it (`finding_places`); `alone` how many of them it is the
    * ONLY place of — those would be left unplaced by a removal; `pinned` the attestations, asset links and place
    * overrides (`places set`, V080) that name it, which keep it out of `--fix` and out of the importer's prune: each is
    * a person's decision about the place.
    */
  final case class Listed(
      id: String,
      label: String,
      source: String,
      kind: String,
      dating: Dating,
      findings: Int = 0,
      alone: Int = 0,
      pinned: Int = 0
  ):
    def detail: String =
      val what = s"$label ($source $kind, ${dating.wire})"
      val held =
        if findings == 0 then ""
        else if alone == 0 then
          s": ${Plural.count(findings.toLong, "finding")}, ${
              if findings == 1 then "also" else "each also"
            } at another place"
        else if findings == 1 then ": 1 finding, at no other place"
        else s": ${Plural.count(findings.toLong, "finding")}, $alone of them at no other place"
      val kept = if pinned > 0 then
        s" — kept: named by $pinned attestation(s)/asset link(s)/place override(s); move or reset those first"
      else ""
      what + held + kept

  /** The rule's report. `count` is the true number of places; the items are the group lines (always), every place
    * holding a finding or pinned by evidence (always — they are what a person must see before a removal), then `sample`
    * up to `maxItems` items in all.
    */
  def report(
      rule: Rule,
      groups: List[Group],
      holders: List[Listed],
      sample: List[Listed],
      maxItems: Int
  ): LintRuleReport =
    val total = groups.map(_.places).sum
    if total == 0 then LintRuleReport(rule.name, rule.description, 0, Nil)
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
  def fixed(removed: Int, remaining: Int, findings: Int, sources: Int): String =
    s"${Lint.PlaceImportedPointArea}: $removed point-only imported area(s) removed" +
      (if findings > 0 then
         s", ${Plural.count(findings.toLong, "finding")} of ${Plural.count(sources.toLong, "source")} matched to places again"
       else "") +
      (if remaining > 0 then s", $remaining still to remove" else "")
