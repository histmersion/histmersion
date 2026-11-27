package com.histmersion
package server

import com.histmersion.api.WebPaths
import com.histmersion.api.Html.html
import com.histmersion.api.instance.BuildId
import com.histmersion.domain.Title
import com.histmersion.server.instance.WebBundle
import com.histmersion.util.storage.{Blob, Resources}
import com.histmersion.lint.{tolerated, LintRule}

/** Regression guards for how the web UI is shipped. The UI itself is Scala.js (`modules/web`, with its own suites);
  * these read the served assets off the classpath — exactly as `Static.read` serves them — and assert the properties
  * that once broke the page with no error at all.
  *
  * The precedent is `core`'s "no hardcoded ports in src/main" lint test: a grep over the real assets is a legitimate
  * test when the property IS textual.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class WebAssetsSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def asset(name: String): String = Static.read(Static.ReadPath(s"web/$name")).value

  private val bundle = WebBundle(BuildId("0123456789abcdef0123456789abcdef"))

  test("G109: the bundle's id is read as the build wrote it, its line break dropped; no file, or no id in it, is none"):
    def read(files: (String, String)*) =
      WebBundle.read(Resources.InMemory(files.map((k, v) => Resources.ResourceName(k) -> Blob.Text(v)).toMap)).id
    assertEquals(read("web/build-id.txt" -> "0123456789abcdef0123456789abcdef\n"), bundle.id)
    assertEquals(read(), BuildId(""))
    assertEquals(read("web/build-id.txt" -> "not a build"), BuildId(""))
    assertEquals(WebBundle(BuildId("")).header, BuildId.Header -> "", "an empty header, which no answer carries")
    assertEquals(
      bundle.shell(Static.Asset("""<a href="/x?v={{build}}">{{build}}</a>""")).value,
      """<a href="/x?v=0123456789abcdef0123456789abcdef">0123456789abcdef0123456789abcdef</a>"""
    )

  test("the page mounts the Scala.js app into #app and loads the bundle"):
    val html = asset("index.html")
    assert(html.contains("<div id=\"app\"></div>"), html)
    assert(html.contains("""<script type="module" src="/web/boot.js?v={{build}}"></script>"""), html)
    assert(
      asset("boot.js").contains("app.src = \"/web/app.bundle.js\" + new URL(import.meta.url).search;"),
      asset("boot.js")
    )

  test("G109: the bundle, its id, the page shells and the config name one build; no address carries a fixed ?v="):
    val id = asset("build-id.txt").trim
    assert(id.matches("[0-9a-f]{32}"), s"the build id is 32 hex digits: '$id'")
    val js = asset("app.bundle.js")
    assert(js.contains(s"\"$id\""), "the id is written into the bundle (web PageBuild.compiled)")
    assert(!js.contains("HISTMERSION-BUILD-ID-UNSET"), "the placeholder is left in the bundle")
    val served = WebBundle.read(Resources.classpath(getClass.getClassLoader))
    assertEquals(served.id, BuildId(id))
    assertEquals(served.header, BuildId.Header -> id)
    val shell = served.shell(Static.read(Static.ReadPath("web/index.html"))).value
    assert(shell.contains(s"""<link rel="preload" href="/web/app.bundle.js?v=$id" as="script" />"""), shell)
    assert(shell.contains(s"""<script type="module" src="/web/boot.js?v=$id"></script>"""), shell)
    assert(!shell.contains(WebBundle.Token), shell)
    val page = ArticleHtml.page(Title("t"), html"<p>b</p>", PageKind.values.head, served).markup
    assert(page.contains(s"""<script src="/web/app.bundle.js?v=$id"></script>"""), page)
    val fixed = """app\.bundle\.js\?v=[0-9a-zA-Z]""".r
    val text = Set("scala", "js", "html", "css")
    val roots = List("modules/server/src/main", "modules/web/src/main").map(java.nio.file.Path.of(_))
    val offenders = roots.flatMap { root =>
      val files = java.nio.file.Files.walk(root)
      try
        files.toArray.toList
          .collect { case p: java.nio.file.Path => p }
          .filter(p => java.nio.file.Files.isRegularFile(p) && text.contains(p.toString.split('.').last))
          .filter(p => fixed.findFirstIn(java.nio.file.Files.readString(p)).isDefined)
      finally files.close()
    }
    assertEquals(offenders, Nil)

  test("the page shells carry no inline script: the Content-Security-Policy runs only the server's own files"):
    val inline = "<script(?![^>]*\\bsrc=)[^>]*>".r
    for (name, html) <- List(
        "index.html" -> asset("index.html"),
        "a server page" -> ArticleHtml.page(Title("t"), html"<p>b</p>", PageKind.values.head, bundle).markup
      )
    do assertEquals(inline.findFirstIn(html), None, name)

  test("the web UI's pages, its items' pages and the old list addresses serve the app; anything else does not"):
    def app(p: String) =
      WebPaths.isApp(p.split('/').toList.filter(_.nonEmpty).map(WebPaths.Segment(_)))
    List(
      "/",
      "/explore",
      "/library",
      "/library/upload",
      "/research",
      "/research/goals/bronze-age",
      "/research/activity/jobs",
      "/settings/keys",
      "/place/pleiades:579885",
      "/event/ev1",
      "/run/3f2a",
      "/manager/watch:x",
      "/job/77",
      "/acquire",
      "/tasks",
      "/log",
      "/merge"
    ).foreach(p => assert(app(p), p))
    List("/source/x", "/article/y", "/run", "/run/a/b", "/nope", "/web/style.css", "/api/sources", "/jobs")
      .foreach(p => assert(!app(p), p))

  test("both page shells apply the reader's stored theme before the stylesheet paints"):
    val key = "<script src=\"/web/theme.js\"></script>"
    for (name, html) <- List(
        "index.html" -> asset("index.html"),
        "a server page" -> ArticleHtml.page(Title("t"), html"<p>b</p>", PageKind.values.head, bundle).markup
      )
    do
      val at = html.indexOf(key)
      assert(at > 0, s"$name: no theme script")
      assert(at < html.indexOf("/web/style.css"), s"$name: the theme is applied after the stylesheet")
    val theme = asset("theme.js")
    assert(theme.contains("localStorage.getItem(\"histmersion.theme\")"), theme)
    assert(theme.contains("setAttribute(\"data-theme\",t)") && theme.contains("catch(e)"), theme)
    assert(
      asset("index.html").contains(
        """<link rel="preload" href="/web/vendor/source-fonts/SourceSans3-Regular.woff2" as="font""""
      )
    )

  test("the bundle is a classic script, never type=module (its top-level `this` must be the window)"):
    val html = asset("index.html")
    assert(!"<script[^>]*src=\"[^\"]*app\\.bundle\\.js".r.findFirstIn(html).exists(_.contains("module")), html)
    val boot = asset("boot.js")
    assert(boot.contains("document.createElement(\"script\")"), "the bundle is added as a classic script element")

  test("the map library (an ES module) is on window before the bundle that uses it is added"):
    val boot = asset("boot.js")
    val lib = boot.indexOf("window.maplibregl = maplibregl")
    assert(lib > 0 && lib < boot.indexOf("app.src ="), boot)

  test("the bundle is on the classpath with a source map next to it"):
    val js = asset("app.bundle.js")
    assert(js.length > 10000, s"bundle is only ${js.length} chars")
    assert(js.contains("sourceMappingURL=app.bundle.js.map"), js.takeRight(200))

  test("no inline onclick: a citekey with '-' or '.' must not be spliced into JS"):
    // `[[a-z][a-z0-9_.-]{1,120}]` is the citekey shape (core/Citekey); '-' and '.' are legal and
    // used to be STRIPPED to keep an inline onclick's string literal parseable.
    assert(!asset("index.html").contains("onclick="))
    assert(!asset("app.bundle.js").contains("onclick="))

  test("no animation on a map-positioned element touches transform (it would unpin it)"):
    // the map places its markers with a transform (`.hd-marker`, --tx/--ty); a keyframe that sets
    // `transform` on the marker overrides it for the animation's duration and the element jumps to the
    // pane's origin.
    val css = asset("style.css")
    val keyframes = "@keyframes\\s+([\\w-]+)\\s*\\{((?:[^{}]*\\{[^{}]*\\})*[^{}]*)\\}".r
      .findAllMatchIn(css)
      .map(m => m.group(1) -> m.group(2))
      .toMap
    val leafletPositioned =
      List("hd-marker", "hd-map-label-wrap", "hd-area-point-wrap", "hd-dot-wrap", "hd-point-wrap", "hd-event-wrap")
    for cls <- leafletPositioned do
      val rule = s"\\.$cls[^{]*\\{([^}]*)\\}".r.findAllMatchIn(css).map(_.group(1)).mkString
      val anims = "animation:\\s*([\\w-]+)".r.findAllMatchIn(rule).map(_.group(1)).toList
      for a <- anims do
        assert(!keyframes.getOrElse(a, "").contains("transform"), s".$cls animates transform via @keyframes $a")

  // #882 (headless-Chrome check of the goal, approvals and runs windows at ~400 px): a goal's note
  // with a long URL ran out of the window, a plan's DOI link was the browser's dark blue on the
  // dark panel, and the approval note field was squeezed to a few letters on a phone
  test("window content wraps long words; plan links are readable; the approval note wraps on a phone"):
    val css = asset("style.css")
    def rule(selector: String) =
      css.linesIterator.find(_.startsWith(selector + " {")).getOrElse(fail(s"no rule for $selector"))
    assert(rule(".wm-content").contains("overflow-wrap:break-word"), rule(".wm-content"))
    assert(rule(".approval-plan a, .approval-draft a").contains("color:var(--accent)"))
    assert(rule(".approval-actions .form-row").contains("flex-wrap:wrap"))
    assert(rule("""[data-layout="narrow"] .wm-content .approval-actions .field""").contains("flex:1 1 100%"))

  // #887: a run's steps table (nine columns) scrolled sideways at ~400 px — on a phone it is a card
  // per step, each cell labelled with its column, and nothing scrolls
  test("a run's steps are cards in the narrow layout: no header, labelled cells, no sideways scroll"):
    val css = asset("style.css")
    def rule(selector: String) =
      css.linesIterator.find(_.startsWith(selector + " {")).getOrElse(fail(s"no rule for $selector"))
    val table = rule("""[data-layout="narrow"] .wm-content table.run-steps""")
    assert(table.contains("overflow-x:visible") && table.contains("display:block"), table)
    assert(rule("""[data-layout="narrow"] .run-steps thead""").contains("display:none"))
    assert(rule("""[data-layout="narrow"] .run-steps td::before""").contains("content:attr(data-label)"))
    assert(rule("""[data-layout="narrow"] .run-steps td.is-empty""").contains("display:none"))

  // the minimised-windows bar spanned the whole width and covered the sources/articles panel and its
  // "show N more" button — it ends at the panel's (draggable) width; on a phone the sheet keeps room
  test("the minimised-windows bar never covers the side panel"):
    val css = asset("style.css")
    def rule(selector: String) =
      css.linesIterator.find(_.startsWith(selector + " {")).getOrElse(fail(s"no rule for $selector"))
    val bar = rule(".wm-taskbar")
    assert(bar.contains("right:var(--sidebar-w, 480px)"), bar)
    assert(!bar.contains("right:0"), bar)
    assert(rule("""[data-layout="narrow"] .wm-taskbar""").contains("right:0"))
    assert(rule("""[data-layout="narrow"] .histmersion-app:has(.wm-taskbar) .sidebar""").contains("padding-bottom"))

  // ── one look for every control (the 2026-10 consistency pass) ──────────────────────────────────
  // Buttons in the toolbar, the filter bar, the timeline and the windows each had their own padding,
  // font size and gaps (a toolbar with no padding after its last button, 11px labels next to 13px
  // fields). They are now ONE set of tokens and a few shared classes; these guard that it stays so.

  private lazy val css = asset("style.css")
  private def ruleOf(selector: String): String =
    css.linesIterator.find(_.startsWith(selector + " {")).getOrElse(fail(s"no rule for $selector"))

  /** The full declaration block of the first rule whose selector is exactly `selector`. */
  private def block(selector: String): String =
    val at = css.indexOf("\n" + selector + " {")
    assert(at >= 0, s"no rule for $selector")
    css.substring(at, css.indexOf("}", at))

  test("the control tokens are defined once on :root, and the narrow layout redefines them"):
    val tokens = List(
      "--space-1",
      "--space-4",
      "--space-6",
      "--fs-control",
      "--fs-icon",
      "--control-h",
      "--control-h-sm",
      "--control-px",
      "--radius",
      "--gap-inline",
      "--gap-group",
      "--bar-px",
      "--bar-py",
      "--field-bg",
      "--on-accent"
    )
    val root = """(?s):root\s*\{(.*?)\}""".r.findAllMatchIn(css).map(_.group(1)).mkString
    tokens.foreach(t => assert(root.contains(s"$t:"), s"$t is not defined on :root"))
    val narrow = block("""[data-layout="narrow"]""")
    List("--control-h:var(--tap)", "--fs-control:", "--bar-px:").foreach(t => assert(narrow.contains(t), t))

  test("buttons and fields share one size and one font size: the tokens, not numbers"):
    val btn = block(".btn"); val field = block(".field")
    List(btn, field).foreach { b =>
      assert(b.contains("font-size:var(--fs-control)"), b)
      assert(b.contains("min-height:var(--control-h)"), b)
      assert(b.contains("border-radius:var(--radius)"), b)
    }
    assert(ruleOf(".btn--icon").contains("font-size:var(--fs-icon)"), "an icon button's glyph is a token too")
    List(
      ".btn--primary",
      ".btn--ghost, .btn--toggle",
      ".btn--icon",
      ".btn--sm",
      ".btn--bare",
      ".btn--block",
      ".btn--danger",
      ".btn-group",
      ".toolbar"
    ).foreach(ruleOf)
    assert(ruleOf(".btn:focus-visible").contains("outline:2px solid var(--accent)"), "keyboard focus ring")
    assert(css.contains(""".btn--toggle[aria-pressed="true"]"""), "a pressed toggle is drawn pressed")

  test("groups are separated by ONE line and a gap, with no padding after the last control"):
    val sep = ruleOf(".toolbar > .btn-group + .btn-group")
    assert(sep.contains("border-left:1px solid var(--line)") && sep.contains("padding-left:var(--gap-group)"), sep)
    val group = ruleOf(".btn-group")
    assert(group.contains("gap:var(--gap-inline)") && group.contains("padding:0") && group.contains("margin:0"), group)
    // every bar pads both of its ends the same (the filters and the layers are panels now, padded as panels)
    assert(block(".tl-ctl").contains("padding:var(--bar-py) var(--bar-px)"))
    assert(block(".filter-body").contains("padding:var(--space-5) var(--space-6)"))

  test("the timeline's bar: playback at its start, the span shown in the middle, the span's buttons at its end"):
    val ctl = block(".tl-ctl")
    assert(ctl.contains("display:grid") && ctl.contains("grid-template-columns:1fr auto 1fr"), ctl)
    assert(block(".tl-ctl-span").contains("justify-self:center"))
    assert(block(".tl-ctl-end").contains("justify-self:end"))

  test("the one-off widget styles the shared classes replaced are gone, from the CSS and the code"):
    val gone = List(
      "layer-btn",
      "tl-btn",
      "action-btn",
      "text-input",
      "item-btn",
      "hd-layers-btn",
      "tl-play-active",
      "layer-label"
    )
    gone.foreach(c => assert(!css.contains(s".$c"), s"style.css still styles .$c"))
    val js = asset("app.bundle.js")
    List("layer-btn", "tl-btn", "action-btn", "text-input", "item-btn").foreach(c =>
      assert(!js.contains("\"" + c) && !js.contains(" " + c + "\""), s"the UI still uses class $c")
    )
    // and what the code uses, the stylesheet defines
    List("btn btn--toggle", "btn btn--ghost", "btn btn--primary", "toolbar list-filters", "field").foreach(c =>
      assert(js.contains(c), s"the bundle no longer uses '$c'")
    )

  test("the toolbar's groups stack in the phone drawer, separated by a line across"):
    val stacked = block("""[data-layout="narrow"] .viewport-toggle .toolbar > .btn-group + .btn-group""")
    assert(stacked.contains("border-left:none") && stacked.contains("border-top:1px solid var(--line)"), stacked)

  // the time rule (2026-10-09): an undated place a time view shows only by the findings dated then is
  // drawn apart from a dated one — the map's class for it is styled, and the bundle uses that class
  test("a place dated by its findings is drawn dashed and lighter"):
    // its shape is drawn by the map dashed and fainter (MapView's polities); its name, in the stylesheet
    assert(ruleOf(".hd-by-findings .hd-area-point").contains("dashed"))
    assert(asset("app.bundle.js").contains("hd-by-findings"), "the map no longer marks a place dated by its findings")
