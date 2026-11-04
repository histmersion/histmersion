package com.histmersion.pluginhost

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
    id = "zz_slice_-1650_-1401",
    label = " Hittite Empire ",
    kind = "polity",
    geometry = Some("""{"type":"Point","coordinates":[33.0,40.0]}"""),
    period = Some(period(-1650, -1401)),
    properties = Some("""{"wikidata":"Q170494"}"""),
    names = List(GazetteerName("Hatti", "hit", Some(period(-1650, -1200)), Some(GazetteerOpenSide.End))),
    relations = List(GazetteerRelation("zz_slice_-1650_-1401", "zz_hittites", period = Some(period(-1650, -1401))))
  )

  test("a place becomes the host's place as it is: label untrimmed, periods and open sides carried, the credit"):
    assertEquals(
      PluginGazetteers.input(slice, " Atlas, CC BY 4.0 "),
      Right(
        Place.Input(
          Place(
            "zz_slice_-1650_-1401",
            " Hittite Empire ",
            "polity",
            Some("""{"type":"Point","coordinates":[33.0,40.0]}"""),
            Some(ApiPeriod(-1650, -1401)),
            Some("Atlas, CC BY 4.0"),
            Some("""{"wikidata":"Q170494"}"""),
            None
          ),
          List(Place.Name("Hatti", "hit", Some(ApiPeriod(-1650, -1200)), Some(OpenSide.End))),
          List(Place.Relation("zz_slice_-1650_-1401", "zz_hittites", "part_of", Some(ApiPeriod(-1650, -1401)), None))
        )
      )
    )

  test("a place's own source wins over the credit; no credit and no source stores none; an empty label is kept"):
    assertEquals(
      PluginGazetteers.input(slice.modify(_.source).setTo(Some("its own")), "credit").map(_.place.source),
      Right(Some("its own"))
    )
    assertEquals(PluginGazetteers.input(slice, "  ").map(_.place.source), Right(None))
    assertEquals(PluginGazetteers.input(slice.modify(_.label).setTo(""), "c").map(_.place.label), Right(""))
    assertEquals(
      PluginGazetteers.input(slice.modify(_.open).setTo(Some(GazetteerOpenSide.Start)), "c").map(_.place.open),
      Right(Some(OpenSide.Start))
    )

  test("what the store could not keep is refused, naming the place"):
    def refusal(p: GazetteerPlace) = PluginGazetteers.input(p, "c").left.toOption
    assertEquals(refusal(slice.modify(_.id).setTo(" ")), Some("a place with no id"))
    assertEquals(refusal(slice.modify(_.kind).setTo("")), Some("place 'zz_slice_-1650_-1401': no kind"))
    assertEquals(refusal(slice.modify(_.label).setTo(null)), Some("place 'zz_slice_-1650_-1401': no label"))
    assertEquals(
      refusal(slice.modify(_.geometry).setTo(Some("POINT(1 2)"))),
      Some("place 'zz_slice_-1650_-1401': its geometry is not a JSON object")
    )
    assertEquals(
      refusal(slice.modify(_.properties).setTo(Some("[1]"))),
      Some("place 'zz_slice_-1650_-1401': its properties is not a JSON object")
    )
    assertEquals(
      refusal(slice.modify(_.period).setTo(None).modify(_.open).setTo(Some(GazetteerOpenSide.End))),
      Some("place 'zz_slice_-1650_-1401': an open side without a period")
    )
    assertEquals(
      refusal(slice.modify(_.relations).setTo(List(GazetteerRelation("zz_slice_-1650_-1401", "")))),
      Some("place 'zz_slice_-1650_-1401': a relation without both its places")
    )
    assertEquals(
      refusal(slice.modify(_.relations).setTo(List(GazetteerRelation("a", "b", kind = " ")))),
      Some("place 'zz_slice_-1650_-1401': the relation to 'b' has no kind")
    )
    assertEquals(
      refusal(slice.modify(_.names).setTo(List(GazetteerName(null)))),
      Some("place 'zz_slice_-1650_-1401': a name that is null")
    )
    assertEquals(refusal(null), Some("a place that is null"))

  private def source(answer: => Either[String, List[GazetteerPlace]]): GazetteerSource = new GazetteerSource:
    def title = "Atlas"
    def rights = GazetteerRights(Licence("CC BY 4.0"), "Atlas, CC BY 4.0")
    def fetch(http: Plugin.Http) = answer

  private val offline = new Plugin.Http { def get(url: String, headers: Map[String, String]) = Left("offline") }

  test("a source's places in its order, the refused ones apart; its failure and what it throws are the import's"):
    val bad = slice.modify(_.id).setTo("zz_bad").modify(_.kind).setTo("")
    val other = slice.modify(_.id).setTo("zz_other")
    assertEquals(
      PluginGazetteers
        .places(source(Right(List(slice, bad, other))), offline)
        .map((in, out) => (in.map(_.place.id), out)),
      Right((List("zz_slice_-1650_-1401", "zz_other"), List("place 'zz_bad': no kind")))
    )
    assertEquals(PluginGazetteers.places(source(Left("HTTP 503 from x")), offline), Left("HTTP 503 from x"))
    assertEquals(
      PluginGazetteers.places(source(throw IllegalStateException("boom")), offline),
      Left("the plugin failed: IllegalStateException: boom")
    )
    assertEquals(PluginGazetteers.places(source(null), offline), Left("the plugin answered nothing"))

  test("the download client: the gazetteer import's User-Agent, asked once, the answer as it came"):
    val sent = scala.collection.mutable.ListBuffer.empty[(String, Map[String, String])]
    val wire = new HttpTransport:
      def get(url: String, headers: Map[String, String]) =
        sent += url -> headers
        Right(HttpAnswer(503, Map("retry-after" -> "1"), "busy".getBytes("UTF-8")))
    val r = PluginGazetteers.http(wire, HostLog.scribe).fetch("https://data.example/x.zip")
    assertEquals(r.map(_.status), Right(503))
    assertEquals(sent.toList, List("https://data.example/x.zip" -> Map("User-Agent" -> PluginGazetteers.UserAgent)))
    assert(PluginGazetteers.UserAgent.matches("Histmersion/\\S+ \\(gazetteer import\\)"), PluginGazetteers.UserAgent)
