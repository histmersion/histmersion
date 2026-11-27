package com.histmersion
package api.media
import com.histmersion.api.given
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
object DataCredits:
  private val odbl = Some("https://opendatacommons.org/licenses/odbl/1-0/")
  private val ccBy4 = Some("https://creativecommons.org/licenses/by/4.0/")
  private val ccBy3 = Some("https://creativecommons.org/licenses/by/3.0/")
  private val cc0 = Some("https://creativecommons.org/publicdomain/zero/1.0/")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[DataCredit] = List(
    DataCredit(
      DataCredit.Name("OpenStreetMap"),
      DataCredit.UsedFor("the base map (online tiles, and the Readable and Physical styles through Protomaps)"),
      DataCredit.Licence("ODbL 1.0"),
      (odbl).map(refactorValue11 => DataCredit.LicenceUrl(refactorValue11)),
      MediaItem.Credit("© OpenStreetMap contributors"),
      DataCredit.Url("https://www.openstreetmap.org/copyright")
    ),
    DataCredit(
      DataCredit.Name("Protomaps basemap"),
      DataCredit.UsedFor(
        "the Readable and Physical styles; the extract kept here is an ODbL derivative of " +
          "OpenStreetMap, filtered by basemap.vector.drop"
      ),
      DataCredit.Licence("BSD (code), ODbL (data)"),
      (odbl).map(refactorValue2 => DataCredit.LicenceUrl(refactorValue2)),
      MediaItem.Credit("Protomaps"),
      DataCredit.Url("https://protomaps.com")
    ),
    DataCredit(
      DataCredit.Name("ESA WorldCover (via Daylight landcover)"),
      DataCredit.UsedFor("the land cover drawn at low zoom"),
      DataCredit.Licence("CC BY 4.0"),
      (ccBy4).map(refactorValue3 => DataCredit.LicenceUrl(refactorValue3)),
      MediaItem.Credit(
        "© ESA WorldCover project 2020 / Contains modified Copernicus Sentinel data (2020) processed by ESA WorldCover consortium"
      ),
      DataCredit.Url("https://esa-worldcover.org/en/data-access")
    ),
    DataCredit(
      DataCredit.Name("Mapterhorn terrain (Copernicus GLO-30 and national elevation models)"),
      DataCredit.UsedFor("relief and contours"),
      DataCredit.Licence("per source (Copernicus; CC BY 4.0 and others)"),
      None,
      MediaItem.Credit(
        "Copernicus DEM © DLR e.V. 2010-2014, Airbus Defence and Space GmbH 2014-2018, provided under COPERNICUS by the European Union and ESA"
      ),
      DataCredit.Url("https://mapterhorn.com/attribution")
    ),
    DataCredit(
      DataCredit.Name("ETOPO1 via Mapzen Terrain Tiles"),
      DataCredit.UsedFor("the sea floor"),
      DataCredit.Licence("public domain (NOAA)"),
      None,
      MediaItem.Credit("ETOPO1, U.S. National Oceanic and Atmospheric Administration"),
      DataCredit.Url("https://github.com/tilezen/joerd/blob/master/docs/attribution.md")
    ),
    DataCredit(
      DataCredit.Name("GEBCO_2026 Grid"),
      DataCredit.UsedFor("the sea floor, when chosen"),
      DataCredit.Licence("public domain (GEBCO terms of use)"),
      None,
      MediaItem.Credit("GEBCO Compilation Group (2026) GEBCO_2026 Grid"),
      DataCredit.Url("https://www.gebco.net/data-products/gridded-bathymetry-data")
    ),
    DataCredit(
      DataCredit.Name("Natural Earth"),
      DataCredit.UsedFor("physical names, bundled places, the land outline"),
      DataCredit.Licence("public domain"),
      None,
      MediaItem.Credit("Natural Earth"),
      DataCredit.Url("https://www.naturalearthdata.com")
    ),
    DataCredit(
      DataCredit.Name("Pleiades"),
      DataCredit.UsedFor("ancient places: names, points and place cards"),
      DataCredit.Licence("CC BY 3.0"),
      (ccBy3).map(refactorValue14 => DataCredit.LicenceUrl(refactorValue14)),
      MediaItem.Credit("Pleiades: A Gazetteer of Past Places (pleiades.stoa.org)"),
      DataCredit.Url("https://pleiades.stoa.org/credits")
    ),
    DataCredit(
      DataCredit.Name("GeoNames"),
      DataCredit.UsedFor("places (an extract built on this machine)"),
      DataCredit.Licence("CC BY 4.0"),
      (ccBy4).map(refactorValue15 => DataCredit.LicenceUrl(refactorValue15)),
      MediaItem.Credit("GeoNames (geonames.org)"),
      DataCredit.Url("https://www.geonames.org/about.html")
    ),
    DataCredit(
      DataCredit.Name("Getty Thesaurus of Geographic Names (TGN)"),
      DataCredit.UsedFor("places (an extract built on this machine)"),
      DataCredit.Licence("ODC-By 1.0"),
      Some(DataCredit.LicenceUrl("https://opendatacommons.org/licenses/by/1-0/")),
      MediaItem.Credit(
        "Contains information from the J. Paul Getty Trust, Getty Research Institute, the Getty Thesaurus of Geographic Names, " +
          "which is made available under the ODC Attribution License"
      ),
      DataCredit.Url("https://www.getty.edu/research/tools/vocabularies/tgn/")
    ),
    DataCredit(
      DataCredit.Name("Seshat Cliopatria"),
      DataCredit.UsedFor("polity boundaries by period"),
      DataCredit.Licence("CC BY 4.0"),
      (ccBy4).map(refactorValue6 => DataCredit.LicenceUrl(refactorValue6)),
      MediaItem.Credit("Cliopatria (Seshat Global History Databank), doi:10.1038/s41597-025-04516-9"),
      DataCredit.Url("https://github.com/Seshat-Global-History-Databank/cliopatria")
    ),
    DataCredit(
      DataCredit.Name("Ancient World Mapping Center"),
      DataCredit.UsedFor("classical-world region names"),
      DataCredit.Licence("ODbL 1.0"),
      (odbl).map(refactorValue7 => DataCredit.LicenceUrl(refactorValue7)),
      MediaItem.Credit("Ancient World Mapping Center, University of North Carolina at Chapel Hill"),
      DataCredit.Url("https://github.com/AWMC/geodata")
    ),
    DataCredit(
      DataCredit.Name("OpenHistoricalMap"),
      DataCredit.UsedFor("a few polity outlines"),
      DataCredit.Licence("CC0 unless a feature says otherwise"),
      (cc0).map(refactorValue18 => DataCredit.LicenceUrl(refactorValue18)),
      MediaItem.Credit(
        "Map data courtesy of the OpenHistoricalMap project, in the public domain unless otherwise noted"
      ),
      DataCredit.Url("https://www.openhistoricalmap.org/copyright")
    ),
    DataCredit(
      DataCredit.Name("Wikidata"),
      DataCredit.UsedFor("places, heritage designations, events, picture lookups"),
      DataCredit.Licence("CC0"),
      (cc0).map(refactorValue19 => DataCredit.LicenceUrl(refactorValue19)),
      MediaItem.Credit("Wikidata"),
      DataCredit.Url("https://www.wikidata.org/wiki/Wikidata:Licensing")
    ),
    DataCredit(
      DataCredit.Name("NOAA NCEI natural hazards"),
      DataCredit.UsedFor("earthquakes, tsunamis and eruptions as events"),
      DataCredit.Licence("public domain (U.S. Government work)"),
      None,
      MediaItem.Credit("NOAA National Centers for Environmental Information, doi:10.7289/V5TD9V7K"),
      DataCredit.Url("https://www.ncei.noaa.gov/products/natural-hazards/tsunamis-earthquakes-volcanoes")
    ),
    DataCredit(
      DataCredit.Name("Smithsonian Global Volcanism Program"),
      DataCredit.UsedFor("eruptions as events"),
      DataCredit.Licence("Smithsonian terms of use"),
      None,
      MediaItem.Credit("Global Volcanism Program, Smithsonian Institution"),
      DataCredit.Url("https://volcano.si.edu"),
      onMap = DataCredit.OnMap(true)
    ),
    DataCredit(
      DataCredit.Name("PeriodO"),
      DataCredit.UsedFor("period presets"),
      DataCredit.Licence("CC0"),
      (cc0).map(refactorValue10 => DataCredit.LicenceUrl(refactorValue10)),
      MediaItem.Credit("PeriodO"),
      DataCredit.Url("https://perio.do"),
      onMap = DataCredit.OnMap(false)
    )
  )
