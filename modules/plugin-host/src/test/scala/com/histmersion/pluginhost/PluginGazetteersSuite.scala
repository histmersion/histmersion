package com.histmersion.pluginhost

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.OpenSide
import com.histmersion.api.catalogue.{Period as ApiPeriod, Place}
import com.histmersion.plugin.*

/** The host's reading of a plugin's gazetteer places: each converted to the store's shape unchanged (its source its
  * own, else the dataset's credit), what the store could not keep refused with the reason, a source's failure or
  * exception its import's failure; and the client a download is lent.
  */
class PluginGazetteersSuite extends munit.FunSuite:

  private def period(a: Int, b: Int): Period = Period.from(Year(a), Year(b)).toOption.get

  private val slice = GazetteerPlace(
    id = com.histmersion.plugin.GazetteerPlace.Id("zz_slice_-1650_-1401"),
    label = com.histmersion.plugin.GazetteerPlace.Label(" Hittite Empire "),
    kind = com.histmersion.plugin.GazetteerPlace.Kind("polity"),
    geometry = Some(com.histmersion.plugin.GazetteerPlace.Geometry("""{"type":"Point","coordinates":[33.0,40.0]}""")),
    period = Some(period(-1650, -1401)),
    properties = Some(com.histmersion.plugin.GazetteerPlace.Properties("""{"wikidata":"Q170494"}""")),
    names = List(
      GazetteerName(
        GazetteerName.Name("Hatti"),
        GazetteerName.Lang("hit"),
        Some(period(-1650, -1200)),
        Some(GazetteerOpenSide.End)
      )
    ),
    relations = List(
      GazetteerRelation(
        GazetteerRelation.ChildId("zz_slice_-1650_-1401"),
        GazetteerRelation.ParentId("zz_hittites"),
        period = Some(period(-1650, -1401))
      )
    )
  )

  test("a place becomes the host's place as it is: label untrimmed, periods and open sides carried, the credit"):
    assertEquals(
      PluginGazetteers.input(slice, com.histmersion.domain.Attribution.Credit(" Atlas, CC BY 4.0 ")),
      Right(
        Place.Input(
          Place(
            com.histmersion.api.catalogue.Place.Id("zz_slice_-1650_-1401"),
            com.histmersion.api.catalogue.Place.Label(" Hittite Empire "),
            com.histmersion.api.catalogue.Place.KindValue("polity"),
            Some(Place.Geometry("""{"type":"Point","coordinates":[33.0,40.0]}""")),
            Some(ApiPeriod(com.histmersion.api.catalogue.Year(-1650), com.histmersion.api.catalogue.Year(-1401))),
            Some(com.histmersion.api.catalogue.Place.Source("Atlas, CC BY 4.0")),
            Some(Place.Properties("""{"wikidata":"Q170494"}""")),
            None
          ),
          List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Hatti"), com.histmersion.api.catalogue.Place.Name.Lang("hit"), Some(ApiPeriod(com.histmersion.api.catalogue.Year(-1650), com.histmersion.api.catalogue.Year(-1200))), Some(OpenSide.End))),
          List(Place.Relation(com.histmersion.api.catalogue.Place.Relation.ChildId("zz_slice_-1650_-1401"), com.histmersion.api.catalogue.Place.Relation.ParentId("zz_hittites"), com.histmersion.api.catalogue.Place.Relation.Kind("part_of"), Some(ApiPeriod(com.histmersion.api.catalogue.Year(-1650), com.histmersion.api.catalogue.Year(-1401))), None))
        )
      )
    )

  test("a place's own source wins over the credit; no credit and no source stores none; an empty label is kept"):
    assertEquals(
      PluginGazetteers.input(slice.modify(_.source).setTo(Some(com.histmersion.plugin.GazetteerPlace.Source("its own"))), com.histmersion.domain.Attribution.Credit("credit")).map(_.place.source),
      Right(Some("its own"))
    )
    assertEquals(PluginGazetteers.input(slice, com.histmersion.domain.Attribution.Credit("  ")).map(_.place.source), Right(None))
    assertEquals(PluginGazetteers.input(slice.modify(_.label).setTo(com.histmersion.plugin.GazetteerPlace.Label("")), com.histmersion.domain.Attribution.Credit("c")).map(_.place.label), Right(""))
    assertEquals(
      PluginGazetteers.input(slice.modify(_.open).setTo(Some(GazetteerOpenSide.Start)), com.histmersion.domain.Attribution.Credit("c")).map(_.place.open),
      Right(Some(OpenSide.Start))
    )

  test("what the store could not keep is refused, naming the place"):
    def refusal(p: GazetteerPlace) = PluginGazetteers.input(p, com.histmersion.domain.Attribution.Credit("c")).left.toOption
    assertEquals(refusal(slice.modify(_.id).setTo(com.histmersion.plugin.GazetteerPlace.Id(" "))), Some("a place with no id"))
    assertEquals(refusal(slice.modify(_.kind).setTo(com.histmersion.plugin.GazetteerPlace.Kind(""))), Some("place 'zz_slice_-1650_-1401': no kind"))
    assertEquals(refusal(slice.modify(_.label).setTo(com.histmersion.plugin.GazetteerPlace.Label.assume(null))), Some("place 'zz_slice_-1650_-1401': no label"))
    assertEquals(
      refusal(slice.modify(_.geometry).setTo(Some(com.histmersion.plugin.GazetteerPlace.Geometry("POINT(1 2)")))),
      Some("place 'zz_slice_-1650_-1401': its geometry is not a JSON object")
    )
    assertEquals(
      refusal(slice.modify(_.properties).setTo(Some(com.histmersion.plugin.GazetteerPlace.Properties("[1]")))),
      Some("place 'zz_slice_-1650_-1401': its properties is not a JSON object")
    )
    assertEquals(
      refusal(slice.modify(_.period).setTo(None).modify(_.open).setTo(Some(GazetteerOpenSide.End))),
      Some("place 'zz_slice_-1650_-1401': an open side without a period")
    )
    assertEquals(
      refusal(slice.modify(_.relations).setTo(List(GazetteerRelation(GazetteerRelation.ChildId("zz_slice_-1650_-1401"), GazetteerRelation.ParentId(""))))),
      Some("place 'zz_slice_-1650_-1401': a relation without both its places")
    )
    assertEquals(
      refusal(slice.modify(_.relations).setTo(List(GazetteerRelation(GazetteerRelation.ChildId("a"), GazetteerRelation.ParentId("b"), kind = GazetteerRelation.Kind(" "))))),
      Some("place 'zz_slice_-1650_-1401': the relation to 'b' has no kind")
    )
    assertEquals(
      refusal(slice.modify(_.names).setTo(List(GazetteerName(GazetteerName.Name.assume(null))))),
      Some("place 'zz_slice_-1650_-1401': a name that is null")
    )
    assertEquals(refusal(null), Some("a place that is null"))

  private def source(answer: => Either[GazetteerSource.FetchResultPart1, List[GazetteerPlace]]): GazetteerSource = new GazetteerSource:
    def title = com.histmersion.plugin.GazetteerSource.TitleResult("Atlas")
    def rights = GazetteerRights(Licence("CC BY 4.0"), com.histmersion.plugin.GazetteerRights.Credit("Atlas, CC BY 4.0"))
    def fetch(http: Plugin.Http) = answer

  private val offline = new Plugin.Http {
    def get(
        url: Plugin.Http.GetUrl,
        headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]
    ) = Left(Plugin.Http.GetResultPart1("offline"))
  }

  test("a source's places in its order, the refused ones apart; its failure and what it throws are the import's"):
    val bad = slice
      .modify(_.id)
      .setTo(com.histmersion.plugin.GazetteerPlace.Id("zz_bad"))
      .modify(_.kind)
      .setTo(com.histmersion.plugin.GazetteerPlace.Kind(""))
    val other = slice.modify(_.id).setTo(com.histmersion.plugin.GazetteerPlace.Id("zz_other"))
    assertEquals(
      PluginGazetteers
        .places(source(Right(List(slice, bad, other))), offline)
        .map((in, out) => (in.map(_.place.id), out)),
      Right((List("zz_slice_-1650_-1401", "zz_other"), List("place 'zz_bad': no kind")))
    )
    assertEquals(PluginGazetteers.places(source(Left(GazetteerSource.FetchResultPart1("HTTP 503 from x"))), offline), Left("HTTP 503 from x"))
    assertEquals(
      PluginGazetteers.places(source(throw IllegalStateException("boom")), offline),
      Left("the plugin failed: IllegalStateException: boom")
    )
    assertEquals(PluginGazetteers.places(source(null), offline), Left("the plugin answered nothing"))

  test("the download client: the gazetteer import's User-Agent, asked once, the answer as it came"):
    val sent = scala.collection.mutable.ListBuffer.empty[(String, Map[String, String])]
    val wire = new HttpTransport:
      def get(url: HttpTransport.Url, headers: Map[com.histmersion.domain.CoreQuota.HeaderName, com.histmersion.domain.CoreQuota.HeaderValue]) =
        sent += url.value -> headers.map((name, value) => name.value -> value.value)
        Right(
          HttpAnswer(
            com.histmersion.pluginhost.HttpAnswer.Code(503),
            Map(com.histmersion.domain.CoreQuota.HeaderName("retry-after") -> com.histmersion.domain.CoreQuota.HeaderValue("1")),
            HttpAnswer.Body("busy".getBytes("UTF-8"))
          )
        )
    val r = PluginGazetteers.http(wire, HostLog.scribe).fetch(com.histmersion.plugin.Plugin.Http.FetchUrl("https://data.example/x.zip"))
    assertEquals(r.map(_.status), Right(503))
    assertEquals(sent.toList, List("https://data.example/x.zip" -> Map("User-Agent" -> PluginGazetteers.UserAgent.value)))
    assert(PluginGazetteers.UserAgent.value.matches("Histmersion/\\S+ \\(gazetteer import\\)"), PluginGazetteers.UserAgent)
