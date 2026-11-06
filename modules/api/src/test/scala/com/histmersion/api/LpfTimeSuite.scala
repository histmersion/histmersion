package com.histmersion.api

/** The LPF reader's years and timespans (#1403). Shared: it runs on the JVM AND under Scala.js, so a regex the JS
  * backend cannot compile (a look-behind) fails here rather than in the browser.
  */

import com.histmersion.api.catalogue.{Lpf, Period}
class LpfTimeSuite extends munit.FunSuite:

  private def json(s: String) = io.circe.parser.parse(s).toOption.get
  private def when(s: String) = Lpf.parseWhen(json(s))

  test("parseYear: the spellings LPF and hand-made GeoJSON actually contain"):
    assertEquals(Lpf.parseYear("-1400"), Some(-1400L))
    assertEquals(Lpf.parseYear("1400"), Some(1400L))
    assertEquals(Lpf.parseYear("1400 BCE"), Some(-1400L))
    assertEquals(Lpf.parseYear("1400 BC"), Some(-1400L))
    assertEquals(Lpf.parseYear("1400 b.c."), Some(-1400L))
    assertEquals(Lpf.parseYear("1400 B.C.E."), Some(-1400L))
    assertEquals(Lpf.parseYear("1400BC"), Some(-1400L), "an era word right after the digits")
    assertEquals(Lpf.parseYear("c. 1500 BC"), Some(-1500L))
    assertEquals(Lpf.parseYear("ca. 1500 BCE"), Some(-1500L))
    assertEquals(Lpf.parseYear("1200 CE"), Some(1200L))
    assertEquals(Lpf.parseYear("AD 800"), Some(800L))
    assertEquals(Lpf.parseYear("a.d. 800"), Some(800L))
    assertEquals(Lpf.parseYear("-0500-01-01"), Some(-501L), "ISO date: year -500 = 501 BCE = historical -501")
    assertEquals(Lpf.parseYear("0500-01-01"), Some(500L))
    assertEquals(Lpf.parseYear("-1400 BCE"), Some(-1400L), "an explicit minus wins over the era word")
    assertEquals(Lpf.parseYear(""), None)
    assertEquals(Lpf.parseYear("unknown"), None)

  test("parseYear: an era word inside another word is no era"):
    assertEquals(Lpf.parseYear("abc 300"), Some(300L), "'bc' preceded by a letter")
    assertEquals(Lpf.parseYear("bcd 300"), Some(300L), "'bc' followed by a letter")
    assertEquals(Lpf.parseYear("300 (bc)"), Some(-300L), "punctuation around it is fine")
    assertEquals(Lpf.parseYear("bc 300"), Some(-300L), "at the very start")

  test("parseWhen: timespans, fuzzy bounds, flat shape, widest span"):
    assertEquals(
      when("""{"timespans":[{"start":{"in":"-1650"},"end":{"in":"-1180"}}]}"""),
      Some(Span(Some(com.histmersion.api.catalogue.Year(-1650)), Some(com.histmersion.api.catalogue.Year(-1180))))
    )
    assertEquals(
      when("""{"timespans":[{"start":{"earliest":"-2000","latest":"-1900"},"end":{"latest":"-1500"}}]}"""),
      Some(Span(Some(com.histmersion.api.catalogue.Year(-2000)), Some(com.histmersion.api.catalogue.Year(-1500)))),
      "fuzzy bounds take the OUTER edge"
    )
    assertEquals(
      when(
        """{"timespans":[{"start":{"in":"-1200"},"end":{"in":"-1000"}},{"start":{"in":"-900"},"end":{"in":"-800"}}]}"""
      ),
      Some(Span(Some(com.histmersion.api.catalogue.Year(-1200)), Some(com.histmersion.api.catalogue.Year(-800)))),
      "several timespans → the widest span"
    )
    assertEquals(when("""{"start":"-300","end":"100"}"""), Some(Span(Some(com.histmersion.api.catalogue.Year(-300)), Some(com.histmersion.api.catalogue.Year(100)))))
    assertEquals(when("""{"start":"100","end":"-300"}"""), Some(Span(Some(com.histmersion.api.catalogue.Year(-300)), Some(com.histmersion.api.catalogue.Year(100)))), "inverted: swapped")
    assertEquals(when("\"-500\""), Some(Span(Some(com.histmersion.api.catalogue.Year(-500)), Some(com.histmersion.api.catalogue.Year(-500)))), "a bare year is that one year")
    assertEquals(when("""{}"""), None)

  test("parseWhen: a one-ended timespan stays open on its other side (#1403)"):
    assertEquals(
      when("""{"timespans":[{"start":{"in":"-1400"}}]}"""),
      Some(Span(Some(com.histmersion.api.catalogue.Year(-1400)), None)),
      "from 1400 BCE on — not the one year 1400 BCE"
    )
    assertEquals(when("""{"timespans":[{"end":{"in":"1769"}}]}"""), Some(Span(None, Some(com.histmersion.api.catalogue.Year(1769)))), "until 1769")
    assertEquals(
      when("""{"timespans":[{"start":{"in":"-800"}},{"start":{"in":"-1200"},"end":{"in":"-1000"}}]}"""),
      Some(Span(Some(com.histmersion.api.catalogue.Year(-1200)), None)),
      "the widest span: an open timespan opens the whole"
    )
    assertEquals(when("""{"start":{"earliest":"-500","latest":"-450"}}"""), Some(Span(Some(com.histmersion.api.catalogue.Year(-500)), None)))

  test("an open side reaches the place, its names and relations, and renders back open"):
    val doc =
      """{"type":"FeatureCollection","features":[
        | {"@id":"x","type":"Feature","properties":{"title":"X"},
        |  "when":{"timespans":[{"start":{"in":"-300"}}]},
        |  "names":[{"toponym":"Old X","when":{"timespans":[{"end":{"in":"200"}}]}},
        |           {"toponym":"New X","when":{"timespans":[{"start":{"in":"1200"},"end":{"in":"1300"}}]}}],
        |  "relations":[{"relationType":"gvp:broaderPartitive","relationTo":"y",
        |                "when":{"timespans":[{"end":{"in":"-100"}}]}}],
        |  "geometry":null}]}""".stripMargin
    val in = Lpf.parse(doc).toOption.get.head
    assertEquals(in.place.bounds, (Some(-300L), None))
    assertEquals(in.place.period, Some(Period(com.histmersion.api.catalogue.Year(-300), com.histmersion.api.catalogue.Year(-300))), "the known year")
    assertEquals(in.place.open, Some(OpenSide.End))
    assertEquals(in.names.find(_.name == "Old X").map(_.bounds), Some((None, Some(200L))))
    assertEquals(in.names.find(_.name == "New X").map(_.bounds), Some((Some(1200L), Some(1300L))))
    assertEquals(in.relations.map(_.bounds), List((None, Some(-100L))))
    assertEquals(Lpf.parse(Lpf.render(List(in))), Right(List(in)), "parse(render(x)) == x with open sides")

  test("properties.period with one side missing stays open too"):
    def per(props: String) =
      Lpf
        .parse(s"""{"type":"FeatureCollection","features":[{"type":"Feature","properties":$props,"geometry":null}]}""")
        .toOption
        .get
        .head
        .place
        .bounds
    assertEquals(per("""{"title":"a","periodStart":-1200}"""), (Some(-1200L), None))
    assertEquals(per("""{"title":"b","maxDate":-330}"""), (None, Some(-330L)))
    assertEquals(per("""{"title":"c","period":[-750,-330]}"""), (Some(-750L), Some(-330L)))

  test("OpenSide: split and bounds"):
    assertEquals(OpenSide.split(Span(Some(com.histmersion.api.catalogue.Year(1)), Some(com.histmersion.api.catalogue.Year(5)))), (Some(Period(com.histmersion.api.catalogue.Year(1), com.histmersion.api.catalogue.Year(5))), None))
    assertEquals(OpenSide.split(Span(Some(com.histmersion.api.catalogue.Year(1)), None)), (Some(Period(com.histmersion.api.catalogue.Year(1), com.histmersion.api.catalogue.Year(1))), Some(OpenSide.End)))
    assertEquals(OpenSide.split(Span(None, Some(com.histmersion.api.catalogue.Year(5)))), (Some(Period(com.histmersion.api.catalogue.Year(5), com.histmersion.api.catalogue.Year(5))), Some(OpenSide.Start)))
    assertEquals(OpenSide.split(Span.always), (None, None))
    assertEquals(OpenSide.bounds(Some(Period(com.histmersion.api.catalogue.Year(3), com.histmersion.api.catalogue.Year(3))), Some(OpenSide.Start)), (None, Some(3L)))
    assertEquals(OpenSide.bounds(None, None), (None, None))
