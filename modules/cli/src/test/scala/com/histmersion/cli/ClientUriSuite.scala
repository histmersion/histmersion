package com.histmersion.cli

import com.histmersion.api.Api
import com.histmersion.config.*
import sttp.client4.*
import sttp.model.Uri
import sttp.tapir.client.sttp4.SttpClientInterpreter
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Filter

/** The CLI's requests are DERIVED from the same `Api` endpoints the server interprets, so this pins the wire spelling
  * of the `Filter` query parameters without needing a server: other streams (web UI, chips, timeline) build the very
  * same URLs by hand.
  */
class ClientUriSuite extends munit.FunSuite:
  private val base = Uri.unsafeParse("http://example.invalid")
  private val interp = SttpClientInterpreter()

  private def uriOf[I](ep: sttp.tapir.PublicEndpoint[I, Api.Error, ?, Any])(in: I): String =
    interp.toRequestThrowDecodeFailures(ep, Some(base)).apply(in).uri.toString

  test("an empty filter sends no query parameters"):
    assertEquals(uriOf(Api.sources)(Filter()), "http://example.invalid/api/sources?sort=relevance")

  test("every filter field maps to its FIXED query parameter name"):
    val f = Filter.of(
      q = Some(com.histmersion.api.library.Filter.Q("collapse")),
      tags = List(com.histmersion.api.library.Filter.Tags("sea-peoples"), com.histmersion.api.library.Filter.Tags("steppe")),
      authors = List(com.histmersion.api.library.Filter.Authors("cline")),
      places = List(com.histmersion.api.library.Filter.Places("Levant")),
      topics = List(com.histmersion.api.library.Filter.Topics("trade")),
      status = List(com.histmersion.api.library.Filter.Status("current")),
      yearFrom = Some(com.histmersion.api.catalogue.Year(2000)),
      yearTo = Some(com.histmersion.api.catalogue.Year(2020)),
      from = Some(com.histmersion.api.catalogue.Year(-1300)),
      to = Some(com.histmersion.api.catalogue.Year(-1100)),
      minLon = Some(com.histmersion.api.catalogue.Longitude(25)),
      minLat = Some(com.histmersion.api.catalogue.Latitude(30)),
      maxLon = Some(com.histmersion.api.catalogue.Longitude(45)),
      maxLat = Some(com.histmersion.api.catalogue.Latitude(42)),
      sort = Some(com.histmersion.api.library.Sort.Wire("cited")),
      limit = Some(com.histmersion.api.library.Filter.Limit(5)),
      offset = Some(com.histmersion.api.library.Filter.Offset(10))
    )
    val u = uriOf(Api.sources)(f)
    List(
      "q=collapse",
      "tag=sea-peoples",
      "tag=steppe",
      "author=cline",
      "place=Levant",
      "topic=trade",
      "status=current",
      "yearFrom=2000",
      "yearTo=2020",
      "from=-1300",
      "to=-1100",
      "minLon=25",
      "minLat=30",
      "maxLon=45",
      "maxLat=42",
      "sort=cited",
      "limit=5",
      "offset=10"
    )
      .foreach(p => assert(u.contains(p), s"$p missing from $u"))

  test("repeatable parameters repeat, they are not comma-joined"):
    val u = uriOf(Api.sources)(Filter(tags = List(com.histmersion.api.library.Filter.Tags("a"), com.histmersion.api.library.Filter.Tags("b"))))
    assertEquals(u.count(_ == '&') >= 1, true, u)
    assert(u.contains("tag=a") && u.contains("tag=b"), u)

  test("facets carries field, prefix and the same filter"):
    val u = uriOf(Api.facets)((com.histmersion.api.Api.Facets.Field("tags"), Some(com.histmersion.api.Api.Facets.Prefix("in")), Filter(status = List(com.histmersion.api.library.Filter.Status("current")))))
    assert(u.startsWith("http://example.invalid/api/facets?"), u)
    assert(u.contains("field=tags") && u.contains("prefix=in") && u.contains("status=current"), u)

  test("/api/findings takes the SAME filter params, plus the legacy `region`"):
    val f = Filter.of(
      q = Some(com.histmersion.api.library.Filter.Q("collapse")),
      tags = List(com.histmersion.api.library.Filter.Tags("steppe")),
      places = List(com.histmersion.api.library.Filter.Places("Levant")),
      from = Some(com.histmersion.api.catalogue.Year(-1300)),
      to = Some(com.histmersion.api.catalogue.Year(-1100)),
      minLon = Some(com.histmersion.api.catalogue.Longitude(170)),
      minLat = Some(com.histmersion.api.catalogue.Latitude(-60)),
      maxLon = Some(com.histmersion.api.catalogue.Longitude(-170)),
      maxLat = Some(com.histmersion.api.catalogue.Latitude(60)),
      limit = Some(Filter.Limit(500))
    )
    val u = uriOf(Api.findings)(Api.FindingsQuery(f, Some(com.histmersion.api.Api.FindingsQuery.Region("Anatolia"))))
    assert(u.startsWith("http://example.invalid/api/findings?"), u)
    List(
      "q=collapse",
      "tag=steppe",
      "place=Levant",
      "from=-1300",
      "to=-1100",
      "minLon=170",
      "minLat=-60",
      "maxLon=-170",
      "maxLat=60",
      "limit=500",
      "region=Anatolia"
    )
      .foreach(p => assert(u.contains(p), s"$p missing from $u"))
    assert(!uriOf(Api.findings)(Api.FindingsQuery.of(Filter())).contains("region="), "empty region sent")

  test("/api/aggregate takes by + unit + the same filter params"):
    val u = uriOf(Api.aggregate)(
      Api.AggregateQuery.time(Filter(tags = List(com.histmersion.api.library.Filter.Tags("steppe")), period = Some(Period(com.histmersion.api.catalogue.Year(-3300), com.histmersion.api.catalogue.Year(-2600)))), Some(com.histmersion.api.Api.AggregateQuery.Unit("sources")))
    )
    assert(u.startsWith("http://example.invalid/api/aggregate?"), u)
    List("by=time", "unit=sources", "tag=steppe", "from=-3300", "to=-2600")
      .foreach(p => assert(u.contains(p), s"$p missing from $u"))

  test("the legacy /api/search endpoint is untouched (the web page uses it)"):
    assertEquals(uriOf(Api.search)((Some(com.histmersion.api.Api.Search.Text("collapse")), Some(com.histmersion.api.Api.Search.Limit(5)))), "http://example.invalid/api/search?q=collapse&limit=5")
