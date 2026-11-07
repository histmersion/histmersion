package com.histmersion.plugins.events

import com.histmersion.plugin.*
import com.histmersion.upstreams.dating.CatalogDating
import io.circe.{ACursor, Json}

import java.util.Locale

/** NOAA NCEI's significant earthquakes, eruptions and tsunamis: its hazard service
  * (`/hazel/hazard-service/api/v1/{earthquakes,volcanoes,tsunamis/events}`), page by page. A part is a page of a
  * dataset, `<dataset>:<page>`, the next page named by the one before; the pages of a dataset are of its newest
  * records, so every one is read at every import.
  */
final class NoaaCatalogue(cfg: NoaaSettings) extends EventCatalogue:
  // a U.S. federal work: public domain; NCEI asks to be cited
  def title = com.histmersion.plugin.EventCatalogue.TitleResult("NOAA NCEI Natural Hazards")
  def licence = Licence("public-domain")
  def citation =
    com.histmersion.plugin.EventCatalogue.CitationResult(
      "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
        "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K"
    )
  def home = com.histmersion.plugin.EventCatalogue.HomeResult("https://www.ngdc.noaa.gov/hazard/")
  override def requestIntervalMs: com.histmersion.plugin.EventCatalogue.RequestIntervalMsResult =
    com.histmersion.plugin.EventCatalogue.RequestIntervalMsResult(cfg.requestIntervalMs.value)
  override def everyPartEachImport: com.histmersion.plugin.EventCatalogue.EveryPartEachImportResult =
    com.histmersion.plugin.EventCatalogue.EveryPartEachImportResult(true)

  def parts(query: CatalogueQuery): List[com.histmersion.plugin.EventCatalogue.PartsResult] =
    cfg.datasetList.map(ds => com.histmersion.plugin.EventCatalogue.PartsResult(s"${ds.value}:1"))

  def read(part: com.histmersion.plugin.EventCatalogue.ReadPart, query: CatalogueQuery, http: Plugin.Http): Either[com.histmersion.plugin.EventCatalogue.ReadResultPart1, CataloguePage] =
    part.value match
      case NoaaCatalogue.Page(dataset, page) =>
        val p = page.toInt
        val ds = NoaaCatalogue.Dataset(dataset)
        CatalogueJson(http)(
          NoaaCatalogue.url(cfg.url, ds, query.from, query.to, NoaaCatalogue.PageNumber(p), cfg.pageSize)
        ).left
          .map(e => com.histmersion.plugin.EventCatalogue.ReadResultPart1(e.value))
          .flatMap(j =>
            NoaaCatalogue.parse(ds, j).left.map(e => com.histmersion.plugin.EventCatalogue.ReadResultPart1(e.value))
          )
          .map((rs, pages) =>
            CataloguePage(rs, Option.when(p < pages.value)(CataloguePage.Next(s"$dataset:${p + 1}")))
          )
      case other =>
        Left(com.histmersion.plugin.EventCatalogue.ReadResultPart1(s"'$other' is no page of NOAA's datasets"))

  /** The item read again from its `raw`, its dataset named by its id (`volcano:1351`). */
  override def reread(stored: CatalogueEvent): Option[CatalogueEvent] =
    stored.raw
      .flatMap(t => io.circe.parser.parse(t.value).toOption)
      .flatMap(j => NoaaCatalogue.datasetOf(stored.id).flatMap(NoaaCatalogue.record(_, j.hcursor)))
      .map(n => n.copy(names = (stored.names ++ n.names).distinct.filterNot(_.value == n.label.value)))

object NoaaCatalogue:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dataset = Dataset.T
  object Dataset extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageNumber = PageNumber.T
  object PageNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Earthquakes: Dataset = Dataset("earthquakes")
  val Volcanoes: Dataset = Dataset("volcanoes")
  val Tsunamis: Dataset = Dataset("tsunamis")
  val Datasets: List[Dataset] = List(Earthquakes, Volcanoes, Tsunamis)

  private val Page = "([a-z]+):(\\d+)".r

  private def path(ds: com.histmersion.plugins.events.NoaaCatalogue.Dataset): com.histmersion.plugins.events.NoaaCatalogue.Path = if ds == Tsunamis then com.histmersion.plugins.events.NoaaCatalogue.Path("tsunamis/events") else com.histmersion.plugins.events.NoaaCatalogue.Path(ds.value)

  /** Pure: one page of a dataset's events up to `to` (and from `from`). */
  def url(base: com.histmersion.plugins.events.NoaaSettings.Url, dataset: com.histmersion.plugins.events.NoaaCatalogue.Dataset, from: Option[com.histmersion.plugin.Year], to: com.histmersion.plugin.Year, page: com.histmersion.plugins.events.NoaaCatalogue.PageNumber, perPage: com.histmersion.plugins.events.NoaaSettings.PageSize): com.histmersion.plugin.Plugin.Http.GetUrl =
    com.histmersion.plugin.Plugin.Http.GetUrl(
      s"${base.value.stripSuffix("/")}/${path(dataset).value}?maxYear=${to.value}${from.fold("")(f => s"&minYear=${f.value}")}" +
        s"&itemsPerPage=${perPage.value}&page=${page.value}"
    )

  /** Pure: one page's events and the number of pages. Tsunamis NOAA marks erroneous (validity −1) or a mere seiche (0)
    * are dropped; very doubtful (1) and questionable (2) ones are `contested`.
    */
  def parse(dataset: com.histmersion.plugins.events.NoaaCatalogue.Dataset, json: Json): Either[com.histmersion.plugins.events.NoaaCatalogue.Problem, (List[CatalogueEvent], com.histmersion.plugins.events.NoaaCatalogue.Count)] =
    val c = json.hcursor
    c.downField("items").focus.flatMap(_.asArray) match
      case None        => Left(com.histmersion.plugins.events.NoaaCatalogue.Problem("no items in the answer"))
      case Some(items) =>
        Right((items.toList.flatMap(i => record(dataset, i.hcursor)), Count(c.get[Int]("totalPages").getOrElse(1))))

  /** Pure: the dataset a NOAA record's id names (`volcano:1351` → volcanoes). */
  def datasetOf(providerId: com.histmersion.plugin.CatalogueEvent.Id): Option[com.histmersion.plugins.events.NoaaCatalogue.Dataset] =
    providerId.value.takeWhile(_ != ':') match
      case "earthquake" => Some(Earthquakes)
      case "volcano"    => Some(Volcanoes)
      case "tsunami"    => Some(Tsunamis)
      case _            => None

  /** Pure: "SYRIA:  UGARIT" → "Syria: Ugarit" — NOAA's upper-case place names in title case. */
  def titleCase(s: com.histmersion.plugins.events.JsonFields.Text): com.histmersion.plugins.events.NoaaCatalogue.Title =
    Title(
      "(^|[\\s(/,-])(\\p{L})".r.replaceAllIn(
        s.value.trim.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT),
        m => java.util.regex.Matcher.quoteReplacement(m.group(1) + m.group(2).toUpperCase(Locale.ROOT))
      )
    )

  /** Pure: one item of a dataset's answer as an event. */
  def record(dataset: com.histmersion.plugins.events.NoaaCatalogue.Dataset, i: ACursor): Option[CatalogueEvent] =
    import JsonFields.*
    val kind = dataset match
      case Earthquakes => "earthquake"
      case Volcanoes   => "eruption"
      case _           => "tsunami"
    val validity = long(i, com.histmersion.plugins.events.JsonFields.Key("eventValidity"))
    for
      id <- long(i, com.histmersion.plugins.events.JsonFields.Key("id"))
      year <- long(i, JsonFields.Key("year"))
      if dataset != Tsunamis || validity.forall(_.value >= 1)
    yield
      val where = str(i, JsonFields.Key("locationName")).orElse(str(i, JsonFields.Key("name"))).map(t => titleCase(t).value).getOrElse("unknown place")
      val one = singular(dataset).value
      val label = dataset match
        case Volcanoes => s"Eruption of ${str(i, JsonFields.Key("name")).map(_.value).getOrElse(where)}"
        case _         => s"${one.capitalize}, $where"
      val deaths = long(i, JsonFields.Key("deathsTotal")).orElse(long(i, JsonFields.Key("deaths")))
      val d = Dated.of(CatalogDating.span(Some(CatalogDating.year(CatalogDating.HistoricalYear(year.value))), None))
      CatalogueEvent(
        id = CatalogueEvent.Id(s"$one:${id.value}"),
        url = CatalogueEvent.Url(
          s"https://www.ngdc.noaa.gov/hazel/view/hazards/${
              if dataset == Tsunamis then "tsunami" else one
            }/event-more-info/${id.value}"
        ),
        label = CatalogueEvent.Label(label),
        kind = CatalogueEvent.Kind(kind),
        notBefore = d.notBefore,
        notAfter = d.notAfter,
        beginBy = d.beginBy,
        endFrom = d.endFrom,
        lat = latitude(dbl(i, JsonFields.Key("latitude"))),
        lon = longitude(dbl(i, JsonFields.Key("longitude"))),
        historicity = CatalogueEvent.Historicity(if validity.exists(_.value <= 2) then "contested" else "attested"),
        // a tsunami's record carries the magnitude of the earthquake behind it: not the tsunami's size
        vei = long(i, JsonFields.Key("vei")).map(v => CatalogueEvent.Vei(v.value.toInt)).filter(_ => dataset == Volcanoes),
        magnitude = dbl(i, JsonFields.Key("eqMagnitude")).map(m => CatalogueEvent.Magnitude(m.value)).filter(_ => dataset == Earthquakes),
        deaths = deaths.map(n => CatalogueEvent.Deaths(n.value)),
        names = Option.when(dataset == Volcanoes)(str(i, JsonFields.Key("name")).map(n => CatalogueEvent.Names(s"${n.value} eruption"))).flatten.toList,
        description = Some(
          CatalogueEvent.Description(
            (List(s"$where") ++ dbl(i, JsonFields.Key("eqMagnitude")).map(m =>
              if dataset == Earthquakes then s"magnitude ${m.value}" else s"caused by an earthquake of magnitude ${m.value}"
            ) ++
              long(i, JsonFields.Key("vei")).map(v => s"VEI ${v.value}") ++ deaths.map(n => s"${n.value} deaths recorded") ++
              dbl(i, JsonFields.Key("maxWaterHeight")).map(h => s"max. water height ${h.value} m")).mkString("; ")
          )
        ),
        group = if dataset == Volcanoes then long(i, JsonFields.Key("volcanoLocationNewNum")).map(v => CatalogueEvent.Group(s"volcano:${v.value}")) else None,
        central = Dated.year(CatalogDating.HistoricalYear(year.value)),
        raw = i.focus.map(j => CatalogueEvent.Raw(j.noSpaces))
      )

  private def singular(ds: com.histmersion.plugins.events.NoaaCatalogue.Dataset): com.histmersion.plugins.events.NoaaCatalogue.Name = ds match
    case Earthquakes => com.histmersion.plugins.events.NoaaCatalogue.Name("earthquake")
    case Volcanoes   => com.histmersion.plugins.events.NoaaCatalogue.Name("volcano")
    case _           => com.histmersion.plugins.events.NoaaCatalogue.Name("tsunami")
