package com.histmersion
package plugins.events
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.*
import com.histmersion.upstreams.dating.CatalogDating

import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure

/** NOAA NCEI's significant earthquakes, eruptions and tsunamis: its hazard service
  * (`/hazel/hazard-service/api/v1/{earthquakes,volcanoes,tsunamis/events}`), page by page. A part is a page of a
  * dataset, `<dataset>:<page>`, the next page named by the one before; the pages of a dataset are of its newest
  * records, so every one is read at every import.
  */
final class NoaaCatalogue(cfg: NoaaSettings) extends EventCatalogue:
  // a U.S. federal work: public domain; NCEI asks to be cited
  def title = EventCatalogue.Title("NOAA NCEI Natural Hazards")
  def licence = Licence("public-domain")
  def citation =
    EventCatalogue.Citation(
      "National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami " +
        "and Volcano Databases. NOAA National Centers for Environmental Information. https://doi.org/10.7289/V5TD9V7K"
    )
  def home = EventCatalogue.Home("https://www.ngdc.noaa.gov/hazard/")
  override def requestIntervalMs: EventCatalogue.RequestIntervalMs =
    EventCatalogue.RequestIntervalMs(cfg.requestIntervalMs.value)
  override def everyPartEachImport: EventCatalogue.EveryPartEachImport =
    EventCatalogue.EveryPartEachImport(true)

  def parts(query: CatalogueQuery): List[EventCatalogue.ReadPart] =
    cfg.datasetList.map(ds => EventCatalogue.ReadPart(s"${ds.value}:1"))

  def read(
      part: EventCatalogue.ReadPart,
      query: CatalogueQuery,
      http: Plugin.Http
  ): Either[Problem, CataloguePage] =
    part.value match
      case NoaaCatalogue.Page(dataset, page) =>
        val p = page.toInt
        val ds = NoaaCatalogue.Dataset(dataset)
        CatalogueJson(http)(
          NoaaCatalogue.url(cfg.url, ds, query.from, query.to, NoaaCatalogue.PageNumber(p), cfg.pageSize)
        )
          .flatMap(j => NoaaCatalogue.parse(ds, j))
          .map((rs, pages) => CataloguePage(rs, Option.when(p < pages.value)(CataloguePage.Next(s"$dataset:${p + 1}"))))
      case other =>
        Left(
          Problem.Invalid.Unknown.here(
            Problem.TypeName("NoaaCatalogue.Page"),
            Problem.Raw(s"$other"),
            Vector.empty
          )
        )

  /** The item read again from its `raw`, its dataset named by its id (`volcano:1351`). */
  override def reread(stored: CatalogueEvent): Option[CatalogueEvent] =
    stored.raw
      .flatMap(t => RawJson.parse(t.value).toOption)
      .flatMap(j => NoaaCatalogue.datasetOf(stored.id).flatMap(NoaaCatalogue.record(_, j.cursor)))
      .map(n => n.copy(names = (stored.names ++ n.names).distinct.filterNot(_.value == n.label.value)))

object NoaaCatalogue:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Dataset = Dataset.T
  object Dataset extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Path = Path.T
  object Path extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type PageNumber = PageNumber.T
  object PageNumber extends Newtype[Int, Pure]

  val Earthquakes: Dataset = Dataset("earthquakes")
  val Volcanoes: Dataset = Dataset("volcanoes")
  val Tsunamis: Dataset = Dataset("tsunamis")
  val Datasets: List[Dataset] = List(Earthquakes, Volcanoes, Tsunamis)

  private val Page = "([a-z]+):(\\d+)".r

  private def path(ds: Dataset): Path = if ds == Tsunamis then Path("tsunamis/events") else Path(ds.value)

  /** Pure: one page of a dataset's events up to `to` (and from `from`). */
  def url(
      base: NoaaSettings.Url,
      dataset: Dataset,
      from: Option[Year],
      to: Year,
      page: PageNumber,
      perPage: NoaaSettings.PageSize
  ): Plugin.Http.Url =
    Plugin.Http.Url(
      s"${base.value
          .stripSuffix("/")}/${path(dataset).value}?maxYear=${to.value}${from.fold("")(f => s"&minYear=${f.value}")}" +
        s"&itemsPerPage=${perPage.value}&page=${page.value}"
    )

  /** Pure: one page's events and the number of pages. Tsunamis NOAA marks erroneous (validity −1) or a mere seiche (0)
    * are dropped; very doubtful (1) and questionable (2) ones are `contested`.
    */
  def parse(dataset: Dataset, json: Json): Either[Problem, (List[CatalogueEvent], Count)] =
    val c = json.cursor
    c.downField("items").focus.flatMap(_.asArray) match
      case None        => Left(Problem.Invalid.Missing.here(Problem.Field("items")))
      case Some(items) =>
        Right((items.toList.flatMap(i => record(dataset, i.cursor)), Count(c.get[Int]("totalPages").getOrElse(1))))

  /** Pure: the dataset a NOAA record's id names (`volcano:1351` → volcanoes). */
  def datasetOf(providerId: CatalogueEvent.Id): Option[Dataset] =
    providerId.value.takeWhile(_ != ':') match
      case "earthquake" => Some(Earthquakes)
      case "volcano"    => Some(Volcanoes)
      case "tsunami"    => Some(Tsunamis)
      case _            => None

  /** Pure: "SYRIA:  UGARIT" → "Syria: Ugarit" — NOAA's upper-case place names in title case. */
  def titleCase(s: JsonFields.Text): Title =
    Title(
      "(^|[\\s(/,-])(\\p{L})".r.replaceAllIn(
        s.value.trim.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT),
        m => java.util.regex.Matcher.quoteReplacement(m.group(1) + m.group(2).toUpperCase(Locale.ROOT))
      )
    )

  /** Pure: one item of a dataset's answer as an event. */
  def record(dataset: Dataset, i: Cursor): Option[CatalogueEvent] =
    import JsonFields.*
    val kind = dataset match
      case Earthquakes => "earthquake"
      case Volcanoes   => "eruption"
      case _           => "tsunami"
    val validity = i.long(Key("eventValidity"))
    for
      id <- i.long(Key("id"))
      year <- i.long(JsonFields.Key("year"))
      if dataset != Tsunamis || validity.forall(_.value >= 1)
    yield
      val where = i
        .str(JsonFields.Key("locationName"))
        .orElse(i.str(JsonFields.Key("name")))
        .map(t => titleCase(t).value)
        .getOrElse("unknown place")
      val one = singular(dataset).value
      val label = dataset match
        case Volcanoes => s"Eruption of ${i.str(JsonFields.Key("name")).map(_.value).getOrElse(where)}"
        case _         => s"${one.capitalize}, $where"
      val deaths = i.long(JsonFields.Key("deathsTotal")).orElse(i.long(JsonFields.Key("deaths")))
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
        lat = latitude(i.dbl(JsonFields.Key("latitude"))),
        lon = longitude(i.dbl(JsonFields.Key("longitude"))),
        historicity = CatalogueEvent.Historicity(if validity.exists(_.value <= 2) then "contested" else "attested"),
        // a tsunami's record carries the magnitude of the earthquake behind it: not the tsunami's size
        vei =
          i.long(JsonFields.Key("vei")).map(v => CatalogueEvent.Vei(v.value.toInt)).filter(_ => dataset == Volcanoes),
        magnitude = i
          .dbl(JsonFields.Key("eqMagnitude"))
          .map(m => CatalogueEvent.Magnitude(m.value))
          .filter(_ => dataset == Earthquakes),
        deaths = deaths.map(n => CatalogueEvent.Deaths(n.value)),
        names = Option
          .when(dataset == Volcanoes)(
            i.str(JsonFields.Key("name")).map(n => CatalogueEvent.Names(s"${n.value} eruption"))
          )
          .flatten
          .toList,
        description = Some(
          CatalogueEvent.Description(
            (List(s"$where") ++ i
              .dbl(JsonFields.Key("eqMagnitude"))
              .map(m =>
                if dataset == Earthquakes then s"magnitude ${m.value}"
                else s"caused by an earthquake of magnitude ${m.value}"
              ) ++
              i.long(JsonFields.Key("vei")).map(v => s"VEI ${v.value}") ++ deaths.map(n =>
                s"${n.value} deaths recorded"
              ) ++
              i.dbl(JsonFields.Key("maxWaterHeight")).map(h => s"max. water height ${h.value} m")).mkString("; ")
          )
        ),
        group = if dataset == Volcanoes then
          i.long(JsonFields.Key("volcanoLocationNewNum")).map(v => CatalogueEvent.Group(s"volcano:${v.value}"))
        else None,
        central = Dated.year(CatalogDating.HistoricalYear(year.value)),
        raw = i.focus.map(j => CatalogueEvent.Raw(j.noSpaces))
      )

  private def singular(ds: Dataset): Name = ds match
    case Earthquakes => Name("earthquake")
    case Volcanoes   => Name("volcano")
    case _           => Name("tsunami")
