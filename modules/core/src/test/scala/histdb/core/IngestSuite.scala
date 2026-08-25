package histdb.core

/** Pure tests for the ingest contract (plan §1.8): the four spellings of an image link, the job
  * state machine and the Markdown renderers. No database, no HTTP. */
class IngestSuite extends munit.FunSuite:

  private val md =
    """# Paper
      |
      |![Figure 1](assets/fig-1.png)
      |
      |Text ![](assets/plot.jpg "a title") more.
      |
      |[not an image](assets/fig-1.png) and ![remote](https://example.org/x.png)
      |""".stripMargin

  // ── figures/… → assets/… (the seed importer's normalisation) ─────────────────────────────────
  test("fromFigures rewrites only figures/ image links"):
    val in  = "![a](figures/x.png)\n![b](assets/y.png)\n[c](figures/x.png)"
    val out = AssetLinks.fromFigures(in)
    assert(out.contains("![a](assets/x.png)"), out)
    assert(out.contains("![b](assets/y.png)"), out)
    assert(out.contains("[c](figures/x.png)"), "a plain link is not an image link: " + out)

  // ── assets/… → the served path ───────────────────────────────────────────────────────────────
  test("toWeb points image links at /source/{citekey}/assets/{name}"):
    val out = AssetLinks.toWeb(md, "haak2015_massive")
    assert(out.contains("![Figure 1](/source/haak2015_massive/assets/fig-1.png)"), out)
    assert(out.contains("(/source/haak2015_massive/assets/plot.jpg \"a title\")"), out)

  test("toWeb leaves non-image and foreign links alone"):
    val out = AssetLinks.toWeb(md, "x")
    assert(out.contains("[not an image](assets/fig-1.png)"), out)
    assert(out.contains("![remote](https://example.org/x.png)"), out)

  test("toWeb percent-encodes a citekey and name that need it"):
    assertEquals(AssetLinks.url("a b", "c d.png"), "/source/a%20b/assets/c%20d.png")

  // ── assets/… → absolute (MCP) ────────────────────────────────────────────────────────────────
  test("toAbsolute prefixes the public URL and tolerates a trailing slash"):
    val a = AssetLinks.toAbsolute(md, "ck", "http://host:9/")
    val b = AssetLinks.toAbsolute(md, "ck", "http://host:9")
    assertEquals(a, b)
    assert(a.contains("![Figure 1](http://host:9/source/ck/assets/fig-1.png)"), a)

  test("toAbsolute with a blank public URL degrades to the served path"):
    assertEquals(AssetLinks.toAbsolute("![a](assets/x.png)", "ck", "  "),
      "![a](/source/ck/assets/x.png)")

  test("rewriting is idempotent — a rewritten body does not move again"):
    val once  = AssetLinks.toWeb(md, "ck")
    assertEquals(AssetLinks.toWeb(once, "ck"), once)

  // ── rendered HTML ────────────────────────────────────────────────────────────────────────────
  test("lazyImages adds loading=lazy exactly once"):
    val html = """<p><img src="/source/ck/assets/a.png" alt="A" /></p>"""
    val out  = AssetLinks.lazyImages(html)
    assertEquals(out.sliding("loading=".length).count(_ == "loading="), 1)
    assert(out.startsWith("""<p><img loading="lazy" src="""), out)

  test("lazyImages leaves an <img> that already declares loading alone"):
    val html = """<img loading="eager" src="x">"""
    assertEquals(AssetLinks.lazyImages(html), html)

  // ── names and mime types ─────────────────────────────────────────────────────────────────────
  test("safeName strips directories and unsafe characters"):
    assertEquals(AssetLinks.safeName("a/b/../fig 1!.png"), "fig-1-.png")
    assertEquals(AssetLinks.safeName("c:\\tmp\\x.png"), "x.png")
    assertEquals(AssetLinks.safeName(""), "image.png")
    assertEquals(AssetLinks.safeName("---"), "image.png")

  test("mimeOf maps the extensions the converter emits"):
    assertEquals(AssetLinks.mimeOf("a.PNG"), "image/png")
    assertEquals(AssetLinks.mimeOf("a.jpeg"), "image/jpeg")
    assertEquals(AssetLinks.mimeOf("a.bin"), "application/octet-stream")

  // ── image headers (figures read off disk carry no declared size) ─────────────────────────────
  private def png(w: Int, h: Int): Array[Byte] =
    val b = new Array[Byte](24)
    Array[Byte](0x89.toByte, 'P', 'N', 'G', 13, 10, 26, 10).copyToArray(b)
    List(16 -> w, 20 -> h).foreach { (off, v) =>
      (0 until 4).foreach(i => b(off + i) = ((v >>> (8 * (3 - i))) & 0xff).toByte)
    }
    b

  private def gif(w: Int, h: Int): Array[Byte] =
    val b = new Array[Byte](10)
    "GIF89a".getBytes("US-ASCII").copyToArray(b)
    b(6) = (w & 0xff).toByte; b(7) = ((w >>> 8) & 0xff).toByte
    b(8) = (h & 0xff).toByte; b(9) = ((h >>> 8) & 0xff).toByte
    b

  /** SOI, one APP0 segment to be skipped, then SOF0 carrying height then width. */
  private def jpeg(w: Int, h: Int): Array[Byte] =
    val app0 = Array[Byte](0xff.toByte, 0xe0.toByte, 0, 4, 0, 0)
    val sof0 = Array[Byte](0xff.toByte, 0xc0.toByte, 0, 17, 8,
      ((h >>> 8) & 0xff).toByte, (h & 0xff).toByte, ((w >>> 8) & 0xff).toByte, (w & 0xff).toByte) ++
      new Array[Byte](8)
    Array[Byte](0xff.toByte, 0xd8.toByte) ++ app0 ++ sof0

  test("ImageSize reads PNG, GIF and JPEG headers"):
    assertEquals(ImageSize.of(png(1920, 1080)), Some((1920, 1080)))
    assertEquals(ImageSize.of(gif(640, 480)), Some((640, 480)))
    assertEquals(ImageSize.of(jpeg(800, 600)), Some((800, 600)))

  test("ImageSize gives up rather than guessing"):
    assertEquals(ImageSize.of(Array.empty), None)
    assertEquals(ImageSize.of("not an image at all".getBytes("UTF-8")), None)
    assertEquals(ImageSize.of(png(1, 1).take(20)), None, "a truncated PNG has no readable IHDR")
    // an SOI with no frame header must terminate rather than loop
    assertEquals(ImageSize.of(Array[Byte](0xff.toByte, 0xd8.toByte, 0, 0)), None)

  // ── the job state machine ────────────────────────────────────────────────────────────────────
  test("legal job transitions and nothing else"):
    import JobState.*
    assertEquals(JobState.next(Queued, Running), Some(Running))
    assertEquals(JobState.next(Running, Done), Some(Done))
    assertEquals(JobState.next(Running, Failed), Some(Failed))
    assertEquals(JobState.next(Queued, Failed), Some(Failed))
    // a terminal job never moves again, and nothing skips `running`
    assertEquals(JobState.next(Queued, Done), None)
    assertEquals(JobState.next(Done, Running), None)
    assertEquals(JobState.next(Failed, Done), None)
    assertEquals(JobState.next(Running, Queued), None)

  test("job states round-trip through their wire spelling"):
    JobState.values.foreach(s => assertEquals(JobState.parse(s.wire), Some(s)))
    assertEquals(JobState.parse(" DONE "), Some(JobState.Done))
    assertEquals(JobState.parse("nonsense"), None)

  test("only done and failed are terminal"):
    assertEquals(JobState.values.filter(_.isTerminal).toSet, Set(JobState.Done, JobState.Failed))

  test("JobInfo exposes the state its status names"):
    val j = JobInfo("i", Ingest.KindIngest, "running", Some("ck"))
    assert(!j.isTerminal && !j.succeeded)
    assert(j.copy(status = "done").succeeded)
    // an unparseable status must not read as success
    assert(!j.copy(status = "weird").succeeded)

  // ── Markdown ─────────────────────────────────────────────────────────────────────────────────
  test("job Markdown names the status, the citekey and the failure"):
    val j = JobInfo("abc", Ingest.KindIngest, "failed", Some("ck"), None, Some("converter down"),
      Some("0123456789abcdef"), "t0", "t1")
    val out = IngestMarkdown.job(j)
    assert(out.contains("### job abc · ingest"), out)
    assert(out.contains("**error:** converter down"), out)
    assert(out.contains("012345678"), out)

  test("empty listings say so rather than rendering an empty table"):
    assertEquals(IngestMarkdown.jobs(Vector.empty), "_No jobs._")
    assert(IngestMarkdown.assets("ck", Vector.empty).contains("No assets stored"))

  test("asset Markdown carries the URL each figure is served at"):
    val out = IngestMarkdown.assets("ck", Vector(AssetInfo("f.png", "image/png", 120, "sha", Some(4), Some(2))))
    assert(out.contains("/source/ck/assets/f.png"), out)
    assert(out.contains("4×2"), out)
