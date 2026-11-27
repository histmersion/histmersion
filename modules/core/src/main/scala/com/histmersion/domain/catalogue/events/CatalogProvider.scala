package com.histmersion
package domain.catalogue.events
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.{Invalid, Raw}

import com.histmersion.api.catalogue.CatalogProviderInfo
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object CatalogProvider:

  private val GvpName = "gvp"
  private val NoaaName = "noaa"
  private val WikidataName = "wikidata"

  val Gvp: CatalogProviderInfo.Id = CatalogProviderInfo.Id(GvpName)
  val Noaa: CatalogProviderInfo.Id = CatalogProviderInfo.Id(NoaaName)
  val Wikidata: CatalogProviderInfo.Id = CatalogProviderInfo.Id(WikidataName)
  val all: List[CatalogProviderInfo.Id] = List(Gvp, Noaa, Wikidata)

  val infos: List[CatalogProviderInfo] = List(
    // volcano.si.edu (its terms page) did not answer on 2026-10-14; the Smithsonian's general Terms of
    // Use allow non-commercial, educational use with attribution, and the GVP asks to be cited as below.
    CatalogProviderInfo(
      Gvp,
      CatalogProviderInfo.Title("Smithsonian Global Volcanism Program"),
      CatalogProviderInfo.Licence("Smithsonian Terms of Use (non-commercial use with attribution)"),
      CatalogProviderInfo.Citation(
        "Global Volcanism Program, Volcanoes of the World (v. 5.x), Smithsonian Institution, compiled by Venzke, E. " +
          "https://doi.org/10.5479/si.GVP.VOTW5-2024.5.2"
      ),
      CatalogProviderInfo.Home("https://volcano.si.edu")
    ),
    // a U.S. federal work: public domain; NCEI asks to be cited
    CatalogProviderInfo(
      Noaa,
      CatalogProviderInfo.Title("NOAA NCEI Natural Hazards"),
      CatalogProviderInfo.Licence("public-domain"),
      CatalogProviderInfo.Citation(
        "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
          "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K"
      ),
      CatalogProviderInfo.Home("https://www.ngdc.noaa.gov/hazard/")
    ),
    CatalogProviderInfo(
      Wikidata,
      CatalogProviderInfo.Title("Wikidata"),
      CatalogProviderInfo.Licence("CC0-1.0"),
      CatalogProviderInfo.Citation("Wikidata (CC0), https://www.wikidata.org"),
      CatalogProviderInfo.Home("https://www.wikidata.org")
    )
  )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def info(id: CatalogProviderInfo.Id): Option[CatalogProviderInfo] = infos.find(_.id == id)

  /** Pure: the provider a request names (`smithsonian` and `volcano` for gvp, `ncei` for noaa). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: CatalogProviderInfo.Id): Either[Problem, CatalogProviderInfo.Id] =
    s.value.trim.toLowerCase(java.util.Locale.ROOT) match
      case GvpName | "smithsonian" | "volcano" => Right(Gvp)
      case NoaaName | "ncei"                   => Right(Noaa)
      case WikidataName | "wd"                 => Right(Wikidata)
      case other                               =>
        Left(Invalid.Broken.here(Invalid.Rule.EventCatalogue(Raw.of(s), all.toVector.map(Raw.of(_)))))
