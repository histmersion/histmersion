package com.histmersion.upstreams

/** Plain text and link-safe names out of a provider's metadata (#1446): Commons' HTML author fields, its tracking
  * parameters, its file titles. Pure.
  */

import com.histmersion.upstreams.media.MediaText
class MediaTextSuite extends munit.FunSuite:

  test("plain text out of Commons' HTML author fields"):
    assertEquals(
      MediaText.plain("""<a href="//commons.wikimedia.org/wiki/User:X" title="User:X">David  Monniaux</a>"""),
      "David Monniaux"
    )
    assertEquals(MediaText.plain("Tom &amp; Jerry &#233;&#x20AC; &nbsp;x"), "Tom & Jerry é€ x")
    assertEquals(MediaText.plain("a" * 400, max = 10).length, 10)
    assertEquals(MediaText.plain(null), "")

  test("tracking parameters are stripped from file URLs, the rest kept"):
    assertEquals(MediaText.stripTracking("https://u/x.jpg?utm_source=a&utm_campaign=b"), "https://u/x.jpg")
    assertEquals(MediaText.stripTracking("https://u/x.jpg?page=2&utm_source=a"), "https://u/x.jpg?page=2")
    assertEquals(MediaText.stripTracking("https://u/x.jpg"), "https://u/x.jpg")

  test("a provider file title becomes a link-safe name"):
    assertEquals(MediaText.safeName("File:Lions Gate (Mycenae).jpg"), "Lions_Gate_Mycenae_.jpg")
    assertEquals(MediaText.safeName("File:"), "file")
