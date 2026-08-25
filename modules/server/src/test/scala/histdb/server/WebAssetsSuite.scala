package histdb.server

/** Regression guards for the web UI. There is no JS test runner in this repo (and adding one for
  * five one-line bugs would be worse than the bugs), so these read the shipped assets off the
  * classpath — exactly as `Static.read` serves them — and assert the properties that were wrong.
  *
  * The precedent is `core`'s "no hardcoded ports in src/main" lint test: a grep over the real
  * sources is a legitimate test when the property IS textual. */
class WebAssetsSuite extends munit.FunSuite:

  private def asset(name: String): String = Static.read(s"web/$name")

  private val jsFiles =
    List("app.js", "article.js", "articles.js", "chips.js", "filters.js", "fmt.js", "map.js",
      "places.js", "results.js", "state.js", "timeline.js", "tree.js", "viewport.js")

  test("timeline sends the WHOLE filter, not just the span (and no nonexistent `region` param)"):
    val js = asset("timeline.js")
    assert(js.contains("...State.toApiParams()"),
      "the timeline strip must be narrowed by the same filter as the sidebar")
    assert(!js.contains("region:"),
      "/api/aggregate has no `region` parameter — places are `place`")

  test("no inline onclick anywhere: a citekey with '-' or '.' must not be spliced into JS"):
    // `[[a-z][a-z0-9_.-]{1,120}]` is the citekey shape (core/Citekey); '-' and '.' are legal and
    // used to be STRIPPED to keep an inline onclick's string literal parseable, which opened the
    // wrong article.
    val offenders = jsFiles.filter(f => asset(f).contains("onclick="))
    assertEquals(offenders, Nil, "use a data-* attribute and a delegated listener")

  test("the map popup carries the citekey in data-ck and reads it back"):
    val js = asset("map.js")
    assert(js.contains("data-ck="), js.linesIterator.filter(_.contains("popupHtml")).mkString)
    assert(js.contains("dataset.ck"), "…and something has to read it back")

  test("a finding's region renders from `regionName`, the field the API actually sends"):
    val js = asset("results.js")
    assert(js.contains("m.regionName"), "FindingMatch.regionName, not .region")
    assert(!js.contains("m.region ?") && !js.contains("m.region)"), "the old, always-undefined field")

  test("every loader guards against a stale response overwriting a newer one"):
    List("app.js", "timeline.js", "tree.js").foreach { f =>
      val js = asset(f)
      assert(js.contains("reqId"), s"$f has no request-id guard")
    }

  test("an antimeridian bbox in the hash is restored, not dropped"):
    val js = asset("viewport.js")
    assert(js.contains("+ 360"), "unwrap the east bound past +180 rather than skipping fitBounds")
