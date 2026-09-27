package com.histmersion.server

/** Regression guards for how the web UI is shipped. The UI itself is Scala.js (`modules/web`,
  * with its own suites); these read the served assets off the classpath — exactly as `Static.read`
  * serves them — and assert the properties that once broke the page with no error at all.
  *
  * The precedent is `core`'s "no hardcoded ports in src/main" lint test: a grep over the real
  * assets is a legitimate test when the property IS textual. */
class WebAssetsSuite extends munit.FunSuite:

  private def asset(name: String): String = Static.read(s"web/$name")

  test("the page mounts the Scala.js app into #app and loads the bundle"):
    val html = asset("index.html")
    assert(html.contains("<div id=\"app\"></div>"), html)
    assert(html.contains("src=\"/web/app.bundle.js"), html)

  test("each bookkeeping list's address serves the app, which opens that list's window"):
    assertEquals(HttpApi.appPaths, Set(List("acquire"), List("tasks"), List("log")))

  test("the bundle is a classic script, never type=module (its top-level `this` must be the window)"):
    val tag = "<script[^>]*app\\.bundle\\.js[^>]*>".r.findFirstIn(asset("index.html")).getOrElse("")
    assert(tag.nonEmpty)
    assert(!tag.contains("module"), tag)

  test("Leaflet and the cluster plugin load before the bundle that uses them"):
    val html = asset("index.html")
    val bundle = html.indexOf("app.bundle.js")
    assert(html.indexOf("leaflet.js") < bundle && html.indexOf("leaflet.markercluster.js") < bundle, html)

  test("the bundle is on the classpath with a source map next to it"):
    val js = asset("app.bundle.js")
    assert(js.length > 10000, s"bundle is only ${js.length} chars")
    assert(js.contains("sourceMappingURL=app.bundle.js.map"), js.takeRight(200))

  test("no inline onclick: a citekey with '-' or '.' must not be spliced into JS"):
    // `[[a-z][a-z0-9_.-]{1,120}]` is the citekey shape (core/Citekey); '-' and '.' are legal and
    // used to be STRIPPED to keep an inline onclick's string literal parseable.
    assert(!asset("index.html").contains("onclick="))
    assert(!asset("app.bundle.js").contains("onclick="))

  test("no animation on a Leaflet-positioned element touches transform (it would unpin it)"):
    // Leaflet places markers and tooltips with an inline translate3d transform; a keyframe that sets
    // `transform` overrides it for the animation's duration and the element jumps to the pane origin.
    val css = asset("style.css")
    val keyframes = "@keyframes\\s+([\\w-]+)\\s*\\{((?:[^{}]*\\{[^{}]*\\})*[^{}]*)\\}".r
      .findAllMatchIn(css).map(m => m.group(1) -> m.group(2)).toMap
    val leafletPositioned = List("hd-polity-label", "hd-site-tip", "hd-overlay-tip", "hd-dot-wrap")
    for cls <- leafletPositioned do
      val rule = s"\\.$cls[^{]*\\{([^}]*)\\}".r.findAllMatchIn(css).map(_.group(1)).mkString
      val anims = "animation:\\s*([\\w-]+)".r.findAllMatchIn(rule).map(_.group(1)).toList
      for a <- anims do
        assert(!keyframes.getOrElse(a, "").contains("transform"), s".$cls animates transform via @keyframes $a")
