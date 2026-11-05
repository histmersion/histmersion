package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.CatalogProviderInfo

object CatalogProvider:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoId = InfoId.T
  object InfoId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart2 = ParseResultPart2.T
  object ParseResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Gvp = "gvp"
  val Noaa = "noaa"
  val Wikidata = "wikidata"
  val all: List[com.histmersion.domain.catalogue.events.CatalogProvider.All] = List(Gvp, Noaa, Wikidata)

  val infos: List[CatalogProviderInfo] = List(
    // volcano.si.edu (its terms page) did not answer on 2026-10-14; the Smithsonian's general Terms of
    // Use allow non-commercial, educational use with attribution, and the GVP asks to be cited as below.
    CatalogProviderInfo(
      Gvp,
      "Smithsonian Global Volcanism Program",
      "Smithsonian Terms of Use (non-commercial use with attribution)",
      "Global Volcanism Program, Volcanoes of the World (v. 5.x), Smithsonian Institution, compiled by Venzke, E. " +
        "https://doi.org/10.5479/si.GVP.VOTW5-2024.5.2",
      "https://volcano.si.edu"
    ),
    // a U.S. federal work: public domain; NCEI asks to be cited
    CatalogProviderInfo(
      Noaa,
      "NOAA NCEI Natural Hazards",
      "public-domain",
      "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
        "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K",
      "https://www.ngdc.noaa.gov/hazard/"
    ),
    CatalogProviderInfo(
      Wikidata,
      "Wikidata",
      "CC0-1.0",
      "Wikidata (CC0), https://www.wikidata.org",
      "https://www.wikidata.org"
    )
  )

  def info(id: com.histmersion.domain.catalogue.events.CatalogProvider.InfoId): Option[CatalogProviderInfo] = infos.find(_.id == id)

  /** Pure: the provider a request names (`smithsonian` and `volcano` for gvp, `ncei` for noaa). */
  def parse(s: com.histmersion.domain.catalogue.events.CatalogProvider.ParseS): Either[com.histmersion.domain.catalogue.events.CatalogProvider.ParseResultPart1, com.histmersion.domain.catalogue.events.CatalogProvider.ParseResultPart2] =
    s.trim.toLowerCase(java.util.Locale.ROOT) match
      case Gvp | "smithsonian" | "volcano" => Right(Gvp)
      case Noaa | "ncei"                   => Right(Noaa)
      case Wikidata | "wd"                 => Right(Wikidata)
      case other                           => Left(s"unknown event catalogue '$other' (one of: ${all.mkString(", ")})")
