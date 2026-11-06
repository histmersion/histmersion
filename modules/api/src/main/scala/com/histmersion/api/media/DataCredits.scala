package com.histmersion.api.media
import com.histmersion.api.given
object DataCredits:
  private val odbl = Some("https://opendatacommons.org/licenses/odbl/1-0/")
  private val ccBy4 = Some("https://creativecommons.org/licenses/by/4.0/")
  private val ccBy3 = Some("https://creativecommons.org/licenses/by/3.0/")
  private val cc0 = Some("https://creativecommons.org/publicdomain/zero/1.0/")

  val all: List[DataCredit] = List(
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("OpenStreetMap"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("the base map (online tiles, and the Readable and Physical styles through Protomaps)"),
      _root_.com.histmersion.api.media.DataCredit.Licence("ODbL 1.0"),
      (odbl).map(refactorValue11 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue11)),
      _root_.com.histmersion.api.media.MediaItem.Credit("© OpenStreetMap contributors"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://www.openstreetmap.org/copyright")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Protomaps basemap"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("the Readable and Physical styles; the extract kept here is an ODbL derivative of " +
        "OpenStreetMap, filtered by basemap.vector.drop"),
      _root_.com.histmersion.api.media.DataCredit.Licence("BSD (code), ODbL (data)"),
      (odbl).map(refactorValue2 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue2)),
      _root_.com.histmersion.api.media.MediaItem.Credit("Protomaps"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://protomaps.com")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("ESA WorldCover (via Daylight landcover)"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("the land cover drawn at low zoom"),
      _root_.com.histmersion.api.media.DataCredit.Licence("CC BY 4.0"),
      (ccBy4).map(refactorValue3 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue3)),
      _root_.com.histmersion.api.media.MediaItem.Credit("© ESA WorldCover project 2020 / Contains modified Copernicus Sentinel data (2020) processed by ESA WorldCover consortium"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://esa-worldcover.org/en/data-access")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Mapterhorn terrain (Copernicus GLO-30 and national elevation models)"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("relief and contours"),
      _root_.com.histmersion.api.media.DataCredit.Licence("per source (Copernicus; CC BY 4.0 and others)"),
      None,
      _root_.com.histmersion.api.media.MediaItem.Credit("Copernicus DEM © DLR e.V. 2010-2014, Airbus Defence and Space GmbH 2014-2018, provided under COPERNICUS by the European Union and ESA"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://mapterhorn.com/attribution")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("ETOPO1 via Mapzen Terrain Tiles"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("the sea floor"),
      _root_.com.histmersion.api.media.DataCredit.Licence("public domain (NOAA)"),
      None,
      _root_.com.histmersion.api.media.MediaItem.Credit("ETOPO1, U.S. National Oceanic and Atmospheric Administration"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://github.com/tilezen/joerd/blob/master/docs/attribution.md")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("GEBCO_2026 Grid"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("the sea floor, when chosen"),
      _root_.com.histmersion.api.media.DataCredit.Licence("public domain (GEBCO terms of use)"),
      None,
      _root_.com.histmersion.api.media.MediaItem.Credit("GEBCO Compilation Group (2026) GEBCO_2026 Grid"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://www.gebco.net/data-products/gridded-bathymetry-data")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Natural Earth"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("physical names, bundled places, the land outline"),
      _root_.com.histmersion.api.media.DataCredit.Licence("public domain"),
      None,
      _root_.com.histmersion.api.media.MediaItem.Credit("Natural Earth"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://www.naturalearthdata.com")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Pleiades"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("ancient places: names, points and place cards"),
      _root_.com.histmersion.api.media.DataCredit.Licence("CC BY 3.0"),
      (ccBy3).map(refactorValue14 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue14)),
      _root_.com.histmersion.api.media.MediaItem.Credit("Pleiades: A Gazetteer of Past Places (pleiades.stoa.org)"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://pleiades.stoa.org/credits")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("GeoNames"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("places (an extract built on this machine)"),
      _root_.com.histmersion.api.media.DataCredit.Licence("CC BY 4.0"),
      (ccBy4).map(refactorValue15 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue15)),
      _root_.com.histmersion.api.media.MediaItem.Credit("GeoNames (geonames.org)"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://www.geonames.org/about.html")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Getty Thesaurus of Geographic Names (TGN)"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("places (an extract built on this machine)"),
      _root_.com.histmersion.api.media.DataCredit.Licence("ODC-By 1.0"),
      Some(_root_.com.histmersion.api.media.DataCredit.LicenceUrl("https://opendatacommons.org/licenses/by/1-0/")),
      _root_.com.histmersion.api.media.MediaItem.Credit("Contains information from the J. Paul Getty Trust, Getty Research Institute, the Getty Thesaurus of Geographic Names, " +
        "which is made available under the ODC Attribution License"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://www.getty.edu/research/tools/vocabularies/tgn/")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Seshat Cliopatria"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("polity boundaries by period"),
      _root_.com.histmersion.api.media.DataCredit.Licence("CC BY 4.0"),
      (ccBy4).map(refactorValue6 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue6)),
      _root_.com.histmersion.api.media.MediaItem.Credit("Cliopatria (Seshat Global History Databank), doi:10.1038/s41597-025-04516-9"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://github.com/Seshat-Global-History-Databank/cliopatria")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Ancient World Mapping Center"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("classical-world region names"),
      _root_.com.histmersion.api.media.DataCredit.Licence("ODbL 1.0"),
      (odbl).map(refactorValue7 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue7)),
      _root_.com.histmersion.api.media.MediaItem.Credit("Ancient World Mapping Center, University of North Carolina at Chapel Hill"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://github.com/AWMC/geodata")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("OpenHistoricalMap"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("a few polity outlines"),
      _root_.com.histmersion.api.media.DataCredit.Licence("CC0 unless a feature says otherwise"),
      (cc0).map(refactorValue18 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue18)),
      _root_.com.histmersion.api.media.MediaItem.Credit("Map data courtesy of the OpenHistoricalMap project, in the public domain unless otherwise noted"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://www.openhistoricalmap.org/copyright")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Wikidata"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("places, heritage designations, events, picture lookups"),
      _root_.com.histmersion.api.media.DataCredit.Licence("CC0"),
      (cc0).map(refactorValue19 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue19)),
      _root_.com.histmersion.api.media.MediaItem.Credit("Wikidata"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://www.wikidata.org/wiki/Wikidata:Licensing")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("NOAA NCEI natural hazards"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("earthquakes, tsunamis and eruptions as events"),
      _root_.com.histmersion.api.media.DataCredit.Licence("public domain (U.S. Government work)"),
      None,
      _root_.com.histmersion.api.media.MediaItem.Credit("NOAA National Centers for Environmental Information, doi:10.7289/V5TD9V7K"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://www.ncei.noaa.gov/products/natural-hazards/tsunamis-earthquakes-volcanoes")
    ),
    DataCredit(
      _root_.com.histmersion.api.media.DataCredit.Name("Smithsonian Global Volcanism Program"),
      _root_.com.histmersion.api.media.DataCredit.UsedFor("eruptions as events"),
      _root_.com.histmersion.api.media.DataCredit.Licence("Smithsonian terms of use"),
      None,
      _root_.com.histmersion.api.media.MediaItem.Credit("Global Volcanism Program, Smithsonian Institution"),
      _root_.com.histmersion.api.media.DataCredit.Url("https://volcano.si.edu"),
      onMap = _root_.com.histmersion.api.media.DataCredit.OnMap(true)
    ),
    DataCredit(_root_.com.histmersion.api.media.DataCredit.Name("PeriodO"), _root_.com.histmersion.api.media.DataCredit.UsedFor("period presets"), _root_.com.histmersion.api.media.DataCredit.Licence("CC0"), (cc0).map(refactorValue10 => _root_.com.histmersion.api.media.DataCredit.LicenceUrl(refactorValue10)), _root_.com.histmersion.api.media.MediaItem.Credit("PeriodO"), _root_.com.histmersion.api.media.DataCredit.Url("https://perio.do"), onMap = _root_.com.histmersion.api.media.DataCredit.OnMap(false))
  )
