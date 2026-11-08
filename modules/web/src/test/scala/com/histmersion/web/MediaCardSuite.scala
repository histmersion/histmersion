package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.TimeSpan
import com.histmersion.api.media.MediaRole
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.media.{MediaCount, MediaGeoHit, MediaProvider, MediaThumb}
import com.histmersion.api.views.{EventDetail, PlaceDetail}
import com.histmersion.api.catalogue.Place
import com.histmersion.api.library.Source
import com.histmersion.api.media.MediaItem
import com.histmersion.api.media.ImageSize
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Title
import com.histmersion.api.library.AuthorName
import com.histmersion.api.catalogue.Event
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.HtmlAtoms
import com.histmersion.api.UiLabels

/** Pictures on the cards and the media layer (#1446): the place and event cards carry their first picture with its
  * credit and licence, an event element previews an event, the layer's filter menus and popup. Pure.
  */
class MediaCardSuite extends munit.FunSuite:

  private val thumb = MediaThumb(
    MediaItem.Uid("0190a0b0-0000-7000-8000-000000000001"),
    MediaThumb.Role(MediaRole.Image),
    MediaThumb.Url("/media/0190a0b0-0000-7000-8000-000000000001/thumb"),
    MediaItem.Credit("Andreas Trepte, CC BY-SA 2.5, via Wikimedia Commons"),
    Some(MediaItem.License("CC-BY-SA-2.5")),
    Some(MediaItem.LicenseUrl("https://creativecommons.org/licenses/by-sa/2.5")),
    Some(MediaItem.SourceUrl("https://commons.wikimedia.org/wiki/File:Lions-Gate-Mycenae.jpg")),
    Some(ImageSize.Width(330)),
    Some(ImageSize.Height(267))
  )

  private val mycenae = PlaceDetail(
    Place
      .Summary(
        Place.Id("pleiades_570491"),
        Place.Summary.Label("Mycenae"),
        Place.Summary.Kind(Place.Kind.Site),
        Some(Year(-1600L)),
        Some(Year(-1100L)),
        None,
        Place.Summary.Names("Mycenae")
      ),
    geometry = Some(Place.Geometry("""{"type":"Point","coordinates":[22.75,37.73]}""")),
    parents = Nil,
    children = Nil,
    findings = Place.Counts.Findings(3),
    asOf = None,
    links = List(
      Place.Link(
        Place.Id("pleiades_570491"),
        Place.Link.Scheme("commons"),
        Place.Link.ExternalId("Mycenae"),
        Place.Link.Relation("see_also")
      )
    ),
    media = List(
      thumb,
      thumb.modify(_.uid).setTo(MediaItem.Uid("x")).modify(_.role).setTo(MediaThumb.Role(MediaRole.LocatorMap))
    )
  )

  test("a place card shows its first picture; its Commons category is a link"):
    val c = Preview.card(mycenae)
    assertEquals(c.media, Some(thumb))
    assert(
      c.links
        .map((label, href) => label.value -> href.value)
        .contains("Commons category Mycenae" -> "https://commons.wikimedia.org/wiki/Category:Mycenae"),
      c.links.toString
    )
    assertEquals(Preview.card(mycenae.modify(_.media).setTo(Nil)).media, None)

  test("a source card shows its cover, else its illustration; none when it has neither (#1446)"):
    val src = Source.Detail(
      Citekey("anthony2007"),
      Title("The Horse, the Wheel, and Language"),
      (List("Anthony, David W.")).map(AuthorName.apply),
      (Some(2007)).map(Year.apply),
      None,
      Source.Status.Current,
      Nil,
      Nil,
      Nil,
      None,
      None,
      None,
      None,
      None
    )
    val cover = MediaThumb(
      MediaItem.Uid("u1"),
      MediaThumb.Role(MediaRole.Cover),
      MediaThumb.Url("/media/u1/thumb"),
      MediaItem.Credit("Cover: the local Calibre library")
    )
    assertEquals(
      Preview.card(Source.WithFindings(src, Nil, illustration = Some(cover))).media,
      Some(cover)
    )
    assertEquals(
      Preview.creditLine(cover),
      ("Cover: the local Calibre library", None),
      "a cover carries no licence link"
    )
    assertEquals(Preview.card(Source.WithFindings(src, Nil)).media, None)
    assertEquals(
      Preview.card(Source.WithFindings(src, Nil)).title,
      "The Horse, the Wheel, and Language"
    )

  test("the credit line under a picture: the credit, and the licence linked to its page (else the file page)"):
    assertEquals(
      Preview.creditLine(thumb),
      (
        "Andreas Trepte, CC BY-SA 2.5, via Wikimedia Commons",
        Some("CC-BY-SA-2.5" -> "https://creativecommons.org/licenses/by-sa/2.5")
      )
    )
    assertEquals(
      Preview
        .creditLine(
          thumb.modify(_.license).setTo(Some(MediaItem.License("public-domain"))).modify(_.licenseUrl).setTo(None)
        )
        ._2,
      Some("public-domain" -> "https://commons.wikimedia.org/wiki/File:Lions-Gate-Mycenae.jpg")
    )
    assertEquals(Preview.creditLine(thumb.modify(_.license).setTo(None))._2, None)

  test("the picture's stored size reserves its space; an unknown size none"):
    assertEquals(Preview.sizeOf(thumb), Some((330, 267)))
    assertEquals(Preview.sizeOf(thumb.modify(_.height).setTo(None)), None)
    assertEquals(Preview.sizeOf(thumb.modify(_.width).setTo(Some(ImageSize.Width(0)))), None)

  test("an element with data-event previews the event (sources, articles and places first)"):
    assertEquals(
      Preview.targetOf(None, None, None, Some(Pages.EventId("fall_of_troy")), None),
      Some(Preview.Target.Event(Preview.Target.Event.Id("fall_of_troy")))
    )
    assertEquals(
      Preview.targetOf(Some(Citekey("a2020_b")), None, None, Some(Pages.EventId("e")), None),
      Some(Preview.Target.Source(Preview.Target.Source.Citekey("a2020_b")))
    )
    assertEquals(Preview.targetOf(None, None, None, Some(Pages.EventId(" ")), None), None)

  test("an event card: label, kind and years, its parent, its description, its Wikidata page and picture"):
    val e = EventDetail(
      EventSummary(
        Event.Id("fall_of_troy"),
        EventSummary.Label("Destruction of Troy VIIa"),
        EventSummary.Kind("destruction"),
        TimeSpan(Some(Year(-1190)), None, None, Some(Year(-1180))),
        EventSummary.Historicity("attested"),
        description = Some(EventSummary.Description("Burnt level."))
      ),
      parent = Some(
        EventSummary(
          Event.Id("collapse"),
          EventSummary.Label("Late Bronze Age collapse"),
          EventSummary.Kind("cultural_shift"),
          TimeSpan(),
          EventSummary.Historicity("attested")
        )
      ),
      wikidata = Some(EventEntry.Wikidata("Q123")),
      media = List(thumb)
    )
    val c = Preview.card(e)
    assertEquals(c.title, "Destruction of Troy VIIa")
    assert(c.byline.value.startsWith("Destruction · "), c.byline.value)
    assert(c.byline.value.endsWith(" · attested"), c.byline.value)
    assertEquals(c.where, "part of Late Bronze Age collapse")
    assertEquals(c.summary, Some("Burnt level."))
    assertEquals(c.links, List("Wikidata Q123" -> "https://www.wikidata.org/wiki/Q123"))
    assertEquals(c.media, Some(thumb))

  test("the media filters: all, then each value with its count; a chosen value no longer counted is kept"):
    val counts = List(
      MediaCount(MediaCount.Key(MediaProvider.Commons), MediaCount.N(4)),
      MediaCount(MediaCount.Key(MediaProvider.Source), MediaCount.N(9))
    )
    assertEquals(
      MediaFilters
        .options(counts, None, k => MediaFilters.ChoiceLabel(MediaProvider.title(MediaProvider.Id(k.value)).value)),
      List("" -> "all", MediaProvider.Commons -> "Wikimedia Commons (4)", MediaProvider.Source -> "source figures (9)")
    )
    assertEquals(
      MediaFilters.options(Nil, Some(MediaCount.Key("CC0-1.0")), k => MediaFilters.ChoiceLabel(k.value)),
      List("" -> "all", "CC0-1.0" -> "CC0-1.0")
    )

  test("the media layer's popup: a figure names its source; a provider's picture shows thumbnail, credit, licence"):
    val fig = MediaGeoHit(
      Some(Citekey("cline2014_year")),
      Bundle.Asset.FileName("fig1.png"),
      Some(MediaItem.Kind("figure")),
      Latitude(37.0),
      Longitude(22.0),
      None,
      None,
      None,
      MediaItem.Uid("u1")
    )
    // the source is the app's source link (its card on hover), not a bare bold citekey
    val f = MediaPopup.html(fig, MediaPopup.PeriodText("1200 BCE"), MediaPopup.Caption(""))
    assert(
      f.value.startsWith(
        s"""<div class="cite-tip-title media-popup-title">${CardIcons.html(Glyphs.Name("image"))}""" +
          HtmlAtoms.sourceLink(Citekey("cline2014_year"), None, "cline2014_year") + " / fig1.png</div>"
      ),
      f.value
    )
    val pic = MediaGeoHit(
      None,
      Bundle.Asset.FileName("Lions-Gate-Mycenae.jpg"),
      Some(MediaItem.Kind("photo")),
      Latitude(37.0),
      Longitude(22.0),
      None,
      None,
      None,
      thumb.uid,
      Some(MediaItem.Provider(MediaProvider.Commons)),
      Some(MediaItem.License("CC-BY-SA-2.5")),
      Some(MediaItem.Credit("A <b>&</b> B")),
      Some(MediaItem.ThumbUrl(thumb.url.value))
    )
    val h = MediaPopup.html(pic, MediaPopup.PeriodText(""), MediaPopup.Caption(""))
    assert(h.value.contains(s"""src="${thumb.url}""""), h.value)
    assert(h.value.contains("A &lt;b&gt;&amp;&lt;/b&gt; B"), "the credit is escaped: " + h.value)
    // the licence by its name, linked to its text — never the stored id as a code token
    assert(!h.value.contains("<code>"), h.value)
    assert(
      h.value.contains(
        """<a class="media-license" href="https://creativecommons.org/licenses/by-sa/2.5/" target="_blank" rel="noopener license">CC BY-SA 2.5</a>"""
      )
        && h.value.contains("Wikimedia Commons"),
      h.value
    )
    // no full-size URL known: the thumbnail opens nothing
    assert(!h.value.contains("data-full"), h.value)
    assert(
      h.value.startsWith(
        s"""<div class="cite-tip-title media-popup-title">${CardIcons.html(
            Glyphs.Name("image")
          )}Lions-Gate-Mycenae.jpg</div>"""
      ),
      "a picture's popup leads with the picture icon: " + h.value
    )

  test("the media layer's popup: a picture with its full size opens it, with title, credit, licence and provider page"):
    val pic = MediaGeoHit(
      None,
      Bundle.Asset.FileName("Lions_Gate_of_Mycenae_seen_from_the_ramp_in_the_morning_light.jpg"),
      Some(MediaItem.Kind("photo")),
      Latitude(37.0),
      Longitude(22.0),
      None,
      None,
      None,
      thumb.uid,
      Some(MediaItem.Provider(MediaProvider.Commons)),
      Some(MediaItem.License("CC-BY-SA-3.0")),
      Some(MediaItem.Credit("Photo: A. Author")),
      Some(MediaItem.ThumbUrl(thumb.url.value)),
      fileUrl = Some(MediaItem.FileUrl(s"/media/${thumb.uid}/file")),
      sourceUrl = Some(MediaItem.SourceUrl("https://commons.wikimedia.org/wiki/File:Lions.jpg")),
      licenseUrl = None,
      author = Some(MediaItem.Author("A. Author"))
    )
    val h = MediaPopup.html(pic, MediaPopup.PeriodText(""), MediaPopup.Caption(""))
    assert(h.value.contains(s"""data-full="/media/${thumb.uid}/file""""), h.value)
    assert(h.value.contains("is-zoomable"), h.value)
    assert(h.value.contains("""data-lb-source-url="https://commons.wikimedia.org/wiki/File:Lions.jpg""""), h.value)
    val item = MediaPopup.full(pic).get
    assertEquals(item.src, s"/media/${thumb.uid}/file")
    assertEquals(item.title, Some(pic.assetName))
    assertEquals(item.provider, Some(MediaProvider.Commons))

  test("a card's picture opens the stored picture full size, with its credit, licence and provider page"):
    val attrs = Preview.fullAttrs(thumb).toMap
    assertEquals(attrs.get(UiLabels.Text("data-full")), Some(s"/media/${thumb.uid}/file"))
    assertEquals(attrs.get(UiLabels.Text("data-lb-credit")).map(_.value), Some(thumb.credit.value))
    assertEquals(attrs.get(UiLabels.Text("data-lb-license")).map(_.value), thumb.license.map(_.value))
