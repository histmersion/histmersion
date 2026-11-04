package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.media.MediaProvider

class LightboxSuite extends munit.FunSuite:

  private val item = Lightbox.Item(
    "/media/u1/file",
    "Lions_Gate.jpg",
    Some("Lions_Gate.jpg"),
    Some("Photo: A. Author, via Commons"),
    Some("A. Author"),
    Some("CC-BY-SA-3.0"),
    None,
    Some("https://commons.wikimedia.org/wiki/File:Lions_Gate.jpg"),
    Some(MediaProvider.Commons)
  )

  test("a thumbnail's attributes carry its full-size view, and read back as the same item") {
    val attrs = Lightbox.attrs(item)
    val parsed = """ ([a-z-]+)="([^"]*)"""".r.findAllMatchIn(attrs).map(m => m.group(1) -> m.group(2)).toMap
    assertEquals(parsed("data-full"), "/media/u1/file")
    assertEquals(Lightbox.fromAttrs(parsed.get, "/media/u1/thumb", item.alt), item)
  }

  test("the attributes are escaped") {
    assert(
      Lightbox.attrs(item.modify(_.title).setTo(Some("a \"b\" <c>"))).contains("""data-lb-title="a &quot;b&quot; &lt;c&gt;"""")
    )
  }

  test("a page's figure and a picture naming its full size open; another image does not") {
    assert(Lightbox.opens("/assets/abc/fig1.png", None))
    assert(Lightbox.opens("/media/u1/thumb", Some("/media/u1/file")))
    assert(!Lightbox.opens("/media/u1/thumb", None))
    assert(!Lightbox.opens("/web/icon.svg", Some("")))
  }

  test("the caption: title, credit, the licence's name linked to its text, the provider's page") {
    val c = Lightbox.captionHtml(item)
    assert(c.contains("""<div class="lightbox-title">Lions_Gate.jpg</div>"""), c)
    // the credit names the author already: not twice
    assert(c.contains("Photo: A. Author, via Commons ·"), c)
    assert(!c.contains("A. Author · Photo"), c)
    assert(c.contains("""href="https://creativecommons.org/licenses/by-sa/3.0/""""), c)
    assert(c.contains(">CC BY-SA 3.0</a>"), c)
    assert(!c.contains("<code>"), c)
    assert(c.contains("""href="https://commons.wikimedia.org/wiki/File:Lions_Gate.jpg""""), c)
    assert(c.contains("view on Wikimedia Commons ↗"), c)
  }

  test("the caption names an author the credit leaves out; a bare figure has none") {
    val c = Lightbox.captionHtml(item.modify(_.credit).setTo(Some("The Met")))
    assert(c.contains("A. Author · The Met"), c)
    assertEquals(Lightbox.captionHtml(Lightbox.Item("/assets/x.png", "fig")), "")
  }
