package com.histmersion
package domain.catalogue.places

import com.histmersion.domain.*

import com.histmersion.api.Plural
import com.histmersion.api.checks.Lint
import com.histmersion.api.given

import com.histmersion.api.checks.Lint.{LintItem, LintRuleReport, Rule}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey

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
  object ReportMaxItems extends Newtype[Int, Pure]
  type FixedResult = FixedResult.T
  object FixedResult extends Newtype[String, Pure]
  type FixedRemoved = FixedRemoved.T
  object FixedRemoved extends Newtype[Int, Pure]
  type FixedRemaining = FixedRemaining.T
  object FixedRemaining extends Newtype[Int, Pure]
  type FixedFindings = FixedFindings.T
  object FixedFindings extends Newtype[Int, Pure]
  type FixedSources = FixedSources.T
  object FixedSources extends Newtype[Int, Pure]

  enum Dating:
    case Dated, EndOnly, Undated

    def wire: Dating.WireResult = this match
      case Dated   => Dating.WireResult("dated")
      case EndOnly => Dating.WireResult("end-only")
      case Undated => Dating.WireResult("undated")

  object Dating:
    type WireResult = WireResult.T
    object WireResult extends Newtype[String, Pure]

    def of(startUnknown: Boolean, lowerInf: Boolean, upperInf: Boolean): Dating =
      if startUnknown then EndOnly
      else if lowerInf && upperInf then Undated
      else Dated

  /** The places of one source, kind and dating. */
  final case class Group(source: Group.Source, kind: Group.Kind, dating: Dating, places: Group.Places)

  object Group:
    type Source = Source.T
    object Source extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Places = Places.T
    object Places extends Newtype[Int, Pure]

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
      findings: Listed.Findings = Listed.Findings(0),
      alone: Listed.Alone = Listed.Alone(0),
      pinned: Listed.Pinned = Listed.Pinned(0)
  ):
    def detail: Listed.DetailResult =
      val what = s"$label ($source $kind, ${dating.wire})"
      val held =
        if findings.value == 0 then ""
        else if alone.value == 0 then
          s": ${Plural.count(Plural.CountN(findings.value.toLong), Plural.CountSingular("finding"))}, ${
              if findings.value == 1 then "also" else "each also"
            } at another place"
        else if findings.value == 1 then ": 1 finding, at no other place"
        else s": ${Plural.count(Plural.CountN(findings.value.toLong), Plural.CountSingular("finding"))}, $alone of them at no other place"
      val kept = if pinned.value > 0 then
        s" — kept: named by $pinned attestation(s)/asset link(s)/place override(s); move or reset those first"
      else ""
      Listed.DetailResult(what + held + kept)

  object Listed:
    type DetailResult = DetailResult.T
    object DetailResult extends Newtype[String, Pure]

    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Source = Source.T
    object Source extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Findings = Findings.T
    object Findings extends Newtype[Int, Pure]
    type Alone = Alone.T
    object Alone extends Newtype[Int, Pure]
    type Pinned = Pinned.T
    object Pinned extends Newtype[Int, Pure]

  /** The rule's report. `count` is the true number of places; the items are the group lines (always), every place
    * holding a finding or pinned by evidence (always — they are what a person must see before a removal), then `sample`
    * up to `maxItems` items in all.
    */
  def report(
      rule: Rule,
      groups: List[Group],
      holders: List[Listed],
      sample: List[Listed],
      maxItems: ReportMaxItems
  ): LintRuleReport =
    val total = groups.map(_.places.value).sum
    if total == 0 then LintRuleReport(rule.name, rule.description, LintRuleReport.Count(0), Nil)
    else
      val groupItems = groups
        .sortBy(g => (g.source.value, g.kind.value, g.dating.ordinal))
        .map(g =>
          LintItem(
            Citekey(s"${g.source} · ${g.kind} · ${g.dating.wire}"),
            LintItem.Detail(Plural.count(Plural.CountN(g.places.value.toLong), Plural.CountSingular("place")).value)
          )
        )
      val must = holders.sortBy(h => (-h.findings.value, h.id.value))
      val seen = must.map(_.id).toSet
      val room = math.max(0, maxItems.value - groupItems.size - must.size)
      val rest = sample.filterNot(l => seen.contains(l.id)).take(room)
      LintRuleReport(
        rule.name,
        rule.description,
        LintRuleReport.Count(total),
        groupItems ++ must.map(listedItem) ++ rest.map(listedItem)
      )

  private def listedItem(l: Listed): LintItem =
    LintItem(Citekey(l.id.value), LintItem.Detail(l.detail.value))

  /** One line for `lint --fix`. */
  def fixed(removed: FixedRemoved, remaining: FixedRemaining, findings: FixedFindings, sources: FixedSources): FixedResult =
    FixedResult(
      s"${Lint.PlaceImportedPointArea}: $removed point-only imported area(s) removed" +
        (if findings.value > 0 then
           s", ${Plural.count(Plural.CountN(findings.value.toLong), Plural.CountSingular("finding"))} of ${Plural.count(Plural.CountN(sources.value.toLong), Plural.CountSingular("source"))} matched to places again"
         else "") +
        (if remaining.value > 0 then s", $remaining still to remove" else "")
    )
