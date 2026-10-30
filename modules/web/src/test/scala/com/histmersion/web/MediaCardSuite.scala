package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.values.{MediaRole, PlaceKind, SourceStatus}

/** Pictures on the cards and the media layer (#1446): the place and event cards carry their first picture with its
  * credit and licence, an event element previews an event, the layer's filter menus and popup. Pure.
  */
class MediaCardSuite extends munit.FunSuite:

  private val thumb = MediaThumb(
    "0190a0b0-0000-7000-8000-000000000001",
    MediaRole.Image,
    "/media/0190a0b0-0000-7000-8000-000000000001/thumb",
    "Andreas Trepte, CC BY-SA 2.5, via Wikimedia Commons",
    Some("CC-BY-SA-2.5"),
    Some("https://creativecommons.org/licenses/by-sa/2.5"),
    Some("https://commons.wikimedia.org/wiki/File:Lions-Gate-Mycenae.jpg"),
    Some(330),
    Some(267)
  )

  private val mycenae = PlaceDetail(
    PlaceSummary("pleiades_570491", "Mycenae", PlaceKind.Site, Some(-1600L), Some(-1100L), None, "Mycenae"),
    geometry = Some("""{"type":"Point","coordinates":[22.75,37.73]}"""),
    parents = Nil,
    children = Nil,
    findings = 3,
    asOf = None,
    links = List(PlaceLink("pleiades_570491", "commons", "Mycenae", "see_also")),
    media = List(thumb, thumb.copy(uid = "x", role = MediaRole.LocatorMap))
  )

  test("a place card shows its first picture; its Commons category is a link"):
    val c = Preview.card(mycenae)
    assertEquals(c.media, Some(thumb))
    assert(
      c.links.contains("Commons category Mycenae" -> "https://commons.wikimedia.org/wiki/Category:Mycenae"),
      c.links.toString
    )
    assertEquals(Preview.card(mycenae.copy(media = Nil)).media, None)

  test("a source card shows its cover, else its illustration; none when it has neither (#1446)"):
    val src = SourceDetail(
      "anthony2007",
      "The Horse, the Wheel, and Language",
      List("Anthony, David W."),
      Some(2007),
      None,
      SourceStatus.Current,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None
    )
    val cover = MediaThumb("u1", MediaRole.Cover, "/media/u1/thumb", "Cover: the local Calibre library")
    assertEquals(Preview.card(SourceWithFindings(src, Nil, illustration = Some(cover))).media, Some(cover))
    assertEquals(
      Preview.creditLine(cover),
      ("Cover: the local Calibre library", None),
      "a cover carries no licence link"
    )
    assertEquals(Preview.card(SourceWithFindings(src, Nil)).media, None)
    assertEquals(Preview.card(SourceWithFindings(src, Nil)).title, "The Horse, the Wheel, and Language")

  test("the credit line under a picture: the credit, and the licence linked to its page (else the file page)"):
    assertEquals(
      Preview.creditLine(thumb),
      (
        "Andreas Trepte, CC BY-SA 2.5, via Wikimedia Commons",
        Some("CC-BY-SA-2.5" -> "https://creativecommons.org/licenses/by-sa/2.5")
      )
    )
    assertEquals(
      Preview.creditLine(thumb.copy(license = Some("public-domain"), licenseUrl = None))._2,
      Some("public-domain" -> "https://commons.wikimedia.org/wiki/File:Lions-Gate-Mycenae.jpg")
    )
    assertEquals(Preview.creditLine(thumb.copy(license = None))._2, None)

  test("the picture's stored size reserves its space; an unknown size none"):
    assertEquals(Preview.sizeOf(thumb), Some((330, 267)))
    assertEquals(Preview.sizeOf(thumb.copy(height = None)), None)
    assertEquals(Preview.sizeOf(thumb.copy(width = Some(0))), None)

  test("an element with data-event previews the event (sources, articles and places first)"):
    assertEquals(
      Preview.targetOf(None, None, None, Some("fall_of_troy"), None),
      Some(Preview.Target.Event("fall_of_troy"))
    )
    assertEquals(Preview.targetOf(Some("a2020_b"), None, None, Some("e"), None), Some(Preview.Target.Source("a2020_b")))
    assertEquals(Preview.targetOf(None, None, None, Some(" "), None), None)

  test("an event card: label, kind and years, its parent, its description, its Wikidata page and picture"):
    val e = EventDetail(
      EventSummary(
        "fall_of_troy",
        "Destruction of Troy VIIa",
        "destruction",
        TimeSpan(Some(-1190), None, None, Some(-1180)),
        "attested",
        description = Some("Burnt level.")
      ),
      parent = Some(EventSummary("collapse", "Late Bronze Age collapse", "cultural_shift", TimeSpan(), "attested")),
      wikidata = Some("Q123"),
      media = List(thumb)
    )
    val c = Preview.card(e)
    assertEquals(c.title, "Destruction of Troy VIIa")
    assert(c.byline.startsWith("Destruction · "), c.byline)
    assert(c.byline.endsWith(" · attested"), c.byline)
    assertEquals(c.where, "part of Late Bronze Age collapse")
    assertEquals(c.summary, Some("Burnt level."))
    assertEquals(c.links, List("Wikidata Q123" -> "https://www.wikidata.org/wiki/Q123"))
    assertEquals(c.media, Some(thumb))

  test("the media filters: all, then each value with its count; a chosen value no longer counted is kept"):
    val counts = List(MediaCount(MediaProvider.Commons, 4), MediaCount(MediaProvider.Source, 9))
    assertEquals(
      MediaFilters.options(counts, None, MediaProvider.title),
      List("" -> "all", MediaProvider.Commons -> "Wikimedia Commons (4)", MediaProvider.Source -> "source figures (9)")
    )
    assertEquals(MediaFilters.options(Nil, Some("CC0-1.0"), identity), List("" -> "all", "CC0-1.0" -> "CC0-1.0"))

  test("the media layer's popup: a figure names its source; a provider's picture shows thumbnail, credit, licence"):
    val fig = MediaGeoHit(Some("cline2014_year"), "fig1.png", Some("figure"), 37.0, 22.0, None, None, None, "u1")
    // the source is the app's source link (its card on hover), not a bare bold citekey
    val f = MediaPopup.html(fig, "1200 BCE", "")
    assert(
      f.startsWith(
        s"""<div class="cite-tip-title media-popup-title">${CardIcons.html("image")}""" +
          com.histmersion.api.HtmlAtoms.sourceLink("cline2014_year", None, "cline2014_year") + " / fig1.png</div>"
      ),
      f
    )
    val pic = MediaGeoHit(
      None,
      "Lions-Gate-Mycenae.jpg",
      Some("photo"),
      37.0,
      22.0,
      None,
      None,
      None,
      thumb.uid,
      Some(MediaProvider.Commons),
      Some("CC-BY-SA-2.5"),
      Some("A <b>&</b> B"),
      Some(thumb.url)
    )
    val h = MediaPopup.html(pic, "", "")
    assert(h.contains(s"""src="${thumb.url}""""), h)
    assert(h.contains("A &lt;b&gt;&amp;&lt;/b&gt; B"), "the credit is escaped: " + h)
    // the licence by its name, linked to its text — never the stored id as a code token
    assert(!h.contains("<code>"), h)
    assert(
      h.contains(
        """<a class="media-license" href="https://creativecommons.org/licenses/by-sa/2.5/" target="_blank" rel="noopener license">CC BY-SA 2.5</a>"""
      )
        && h.contains("Wikimedia Commons"),
      h
    )
    // no full-size URL known: the thumbnail opens nothing
    assert(!h.contains("data-full"), h)
    assert(
      h.startsWith(
        s"""<div class="cite-tip-title media-popup-title">${CardIcons.html("image")}Lions-Gate-Mycenae.jpg</div>"""
      ),
      "a picture's popup leads with the picture icon: " + h
    )

  test("the media layer's popup: a picture with its full size opens it, with title, credit, licence and provider page"):
    val pic = MediaGeoHit(
      None,
      "Lions_Gate_of_Mycenae_seen_from_the_ramp_in_the_morning_light.jpg",
      Some("photo"),
      37.0,
      22.0,
      None,
      None,
      None,
      thumb.uid,
      Some(MediaProvider.Commons),
      Some("CC-BY-SA-3.0"),
      Some("Photo: A. Author"),
      Some(thumb.url),
      fileUrl = Some(s"/media/${thumb.uid}/file"),
      sourceUrl = Some("https://commons.wikimedia.org/wiki/File:Lions.jpg"),
      licenseUrl = None,
      author = Some("A. Author")
    )
    val h = MediaPopup.html(pic, "", "")
    assert(h.contains(s"""data-full="/media/${thumb.uid}/file""""), h)
    assert(h.contains("is-zoomable"), h)
    assert(h.contains("""data-lb-source-url="https://commons.wikimedia.org/wiki/File:Lions.jpg""""), h)
    val item = MediaPopup.full(pic).get
    assertEquals(item.src, s"/media/${thumb.uid}/file")
    assertEquals(item.title, Some(pic.assetName))
    assertEquals(item.provider, Some(MediaProvider.Commons))

  test("a card's picture opens the stored picture full size, with its credit, licence and provider page"):
    val attrs = Preview.fullAttrs(thumb).toMap
    assertEquals(attrs.get("data-full"), Some(s"/media/${thumb.uid}/file"))
    assertEquals(attrs.get("data-lb-credit"), Some(thumb.credit))
    assertEquals(attrs.get("data-lb-license"), thumb.license)
