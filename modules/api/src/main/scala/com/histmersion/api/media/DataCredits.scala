package com.histmersion.api.media
import com.histmersion.api.given
object DataCredits:
  private val odbl = Some("https://opendatacommons.org/licenses/odbl/1-0/")
  private val ccBy4 = Some("https://creativecommons.org/licenses/by/4.0/")
  private val ccBy3 = Some("https://creativecommons.org/licenses/by/3.0/")
  private val cc0 = Some("https://creativecommons.org/publicdomain/zero/1.0/")

  val all: List[DataCredit] = List(
    DataCredit(
      "OpenStreetMap",
      "the base map (online tiles, and the Readable and Physical styles through Protomaps)",
      "ODbL 1.0",
      odbl,
      "© OpenStreetMap contributors",
      "https://www.openstreetmap.org/copyright"
    ),
    DataCredit(
      "Protomaps basemap",
      "the Readable and Physical styles; the extract kept here is an ODbL derivative of " +
        "OpenStreetMap, filtered by basemap.vector.drop",
      "BSD (code), ODbL (data)",
      odbl,
      "Protomaps",
      "https://protomaps.com"
    ),
    DataCredit(
      "ESA WorldCover (via Daylight landcover)",
      "the land cover drawn at low zoom",
      "CC BY 4.0",
      ccBy4,
      "© ESA WorldCover project 2020 / Contains modified Copernicus Sentinel data (2020) processed by ESA WorldCover consortium",
      "https://esa-worldcover.org/en/data-access"
    ),
    DataCredit(
      "Mapterhorn terrain (Copernicus GLO-30 and national elevation models)",
      "relief and contours",
      "per source (Copernicus; CC BY 4.0 and others)",
      None,
      "Copernicus DEM © DLR e.V. 2010-2014, Airbus Defence and Space GmbH 2014-2018, provided under COPERNICUS by the European Union and ESA",
      "https://mapterhorn.com/attribution"
    ),
    DataCredit(
      "ETOPO1 via Mapzen Terrain Tiles",
      "the sea floor",
      "public domain (NOAA)",
      None,
      "ETOPO1, U.S. National Oceanic and Atmospheric Administration",
      "https://github.com/tilezen/joerd/blob/master/docs/attribution.md"
    ),
    DataCredit(
      "GEBCO_2026 Grid",
      "the sea floor, when chosen",
      "public domain (GEBCO terms of use)",
      None,
      "GEBCO Compilation Group (2026) GEBCO_2026 Grid",
      "https://www.gebco.net/data-products/gridded-bathymetry-data"
    ),
    DataCredit(
      "Natural Earth",
      "physical names, bundled places, the land outline",
      "public domain",
      None,
      "Natural Earth",
      "https://www.naturalearthdata.com"
    ),
    DataCredit(
      "Pleiades",
      "ancient places: names, points and place cards",
      "CC BY 3.0",
      ccBy3,
      "Pleiades: A Gazetteer of Past Places (pleiades.stoa.org)",
      "https://pleiades.stoa.org/credits"
    ),
    DataCredit(
      "GeoNames",
      "places (an extract built on this machine)",
      "CC BY 4.0",
      ccBy4,
      "GeoNames (geonames.org)",
      "https://www.geonames.org/about.html"
    ),
    DataCredit(
      "Getty Thesaurus of Geographic Names (TGN)",
      "places (an extract built on this machine)",
      "ODC-By 1.0",
      Some("https://opendatacommons.org/licenses/by/1-0/"),
      "Contains information from the J. Paul Getty Trust, Getty Research Institute, the Getty Thesaurus of Geographic Names, " +
        "which is made available under the ODC Attribution License",
      "https://www.getty.edu/research/tools/vocabularies/tgn/"
    ),
    DataCredit(
      "Seshat Cliopatria",
      "polity boundaries by period",
      "CC BY 4.0",
      ccBy4,
      "Cliopatria (Seshat Global History Databank), doi:10.1038/s41597-025-04516-9",
      "https://github.com/Seshat-Global-History-Databank/cliopatria"
    ),
    DataCredit(
      "Ancient World Mapping Center",
      "classical-world region names",
      "ODbL 1.0",
      odbl,
      "Ancient World Mapping Center, University of North Carolina at Chapel Hill",
      "https://github.com/AWMC/geodata"
    ),
    DataCredit(
      "OpenHistoricalMap",
      "a few polity outlines",
      "CC0 unless a feature says otherwise",
      cc0,
      "Map data courtesy of the OpenHistoricalMap project, in the public domain unless otherwise noted",
      "https://www.openhistoricalmap.org/copyright"
    ),
    DataCredit(
      "Wikidata",
      "places, heritage designations, events, picture lookups",
      "CC0",
      cc0,
      "Wikidata",
      "https://www.wikidata.org/wiki/Wikidata:Licensing"
    ),
    DataCredit(
      "NOAA NCEI natural hazards",
      "earthquakes, tsunamis and eruptions as events",
      "public domain (U.S. Government work)",
      None,
      "NOAA National Centers for Environmental Information, doi:10.7289/V5TD9V7K",
      "https://www.ncei.noaa.gov/products/natural-hazards/tsunamis-earthquakes-volcanoes"
    ),
    DataCredit(
      "Smithsonian Global Volcanism Program",
      "eruptions as events",
      "Smithsonian terms of use",
      None,
      "Global Volcanism Program, Smithsonian Institution",
      "https://volcano.si.edu",
      onMap = true
    ),
    DataCredit("PeriodO", "period presets", "CC0", cc0, "PeriodO", "https://perio.do", onMap = false)
  )
