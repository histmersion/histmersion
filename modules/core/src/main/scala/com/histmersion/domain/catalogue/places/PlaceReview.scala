package com.histmersion
package domain.catalogue.places

import com.histmersion.domain.catalogue.places.placereview.Evidence
import com.histmersion.domain.catalogue.places.placereview.LinkedItem

import com.histmersion.domain.*

import com.histmersion.api.catalogue.Geocode
import com.histmersion.Period
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** What a point-only seed region IS, from what its sources say (`lint --rule place-kind`, 2026-10-09). Pleiades files a
  * people, a civic tribe, a province, a deme and a district alike as a "region" with one representative point; this
  * reads the evidence — Pleiades' feature types, the class and dates of the Wikidata item a place is linked to, a
  * "Withdrawn (duplicate)" title — and proposes what the place should be. Pure: `db.PlaceReviewRepo` fetches the
  * evidence and applies a fix.
  *
  * It reads every seed point whose kind is an area (region, polity, people), every seed "site" whose only Pleiades
  * types are administrative or a people (Pleiades' `province-2` has no settlement kind, so the seed filed "Thessalia
  * (late Roman province)" as a site), and every place Pleiades withdrew. The rules, first match wins:
  *   1. a title "Withdrawn (duplicate): X" (or "Withdrawn duplicate: X") → MERGE into the one seed place called X (by
  *      [[Geocode.key]], so "Creta et Cyrene (Roman province)" is X); none or several → listed for review;
  *   2. another "Withdrawn…" title → review (Pleiades retired it and names no replacement);
  *   3. an administrative type (province, satrapy, Augustan regio, state, diocese) → `polity` (a site that is also a
  *      settlement stays a site: a provincial capital), with a period: the place's own when both ends are stated and
  *      the end is not Pleiades' "still extant" 2100, else the linked Wikidata item's, else none (said so);
  *   4. the type `people` → `people`: a people or civic tribe is a group of persons, not a territory;
  *   5. a settlement type (settlement, deme) → `site` (Peiraieus is a port town and deme), unless the label itself says
  *      region ("Salmydessos (coastal region)");
  *   6. a linked Wikidata item classed as a historical country → listed for review: Wikidata files Brittany and
  *      Messenia as historical countries too.
  * Anything else is left as it is.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object PlaceReview:

  /** Why a place is proposed for a change or a review: the account of the lint's decision, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  /** Pleiades types that make a place an administrative unit. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val PolityTypes: Set[Evidence.FeatureTypes] =
    Set("province", "province-2", "satrapy", "regio-augusti", "state", "diocese").map(Evidence.FeatureTypes(_))

  /** Pleiades types that make a place a people. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val PeopleTypes: Set[Evidence.FeatureTypes] = Set("people").map(Evidence.FeatureTypes(_))

  /** Pleiades types that make a "region" a settlement. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val SiteTypes: Set[Evidence.FeatureTypes] =
    Set("settlement", "settlement-modern", "deme-attic").map(Evidence.FeatureTypes(_))

  /** Pleiades' "still extant" upper bound (`maxDate = 2100`). */
  val ExtantYear: DeepTime.SignedYear = DeepTime.SignedYear(2100L)

  /** The Wikidata classes the extract selects places by (`db/gazetteer`), in words. */
  val WikidataClasses: Map[LinkedItem.ItemClass, LinkedItem.ClassLabel] = Map(
    LinkedItem.ItemClass("Q3024240") -> LinkedItem.ClassLabel("historical country"),
    LinkedItem.ItemClass("Q1620908") -> LinkedItem.ClassLabel("historical region"),
    LinkedItem.ItemClass("Q486972") -> LinkedItem.ClassLabel("human settlement"),
    LinkedItem.ItemClass("Q15661340") -> LinkedItem.ClassLabel("ancient city"),
    LinkedItem.ItemClass("Q839954") -> LinkedItem.ClassLabel("archaeological site")
  )
  val WikidataPolity: LinkedItem.ItemClass = LinkedItem.ItemClass("Q3024240")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[places] def periodText(p: Period): String =
    Year.period(
      Some(Year(p.start.value.toLong)),
      Some(Year(p.end.value.toLong))
    )
