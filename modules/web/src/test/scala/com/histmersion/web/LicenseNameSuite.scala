package com.histmersion.web

class LicenseNameSuite extends munit.FunSuite:

  test("a Creative Commons id reads as the licence's name, with its text's page") {
    assertEquals(LicenseName.label("CC-BY-SA-3.0"), "CC BY-SA 3.0")
    assertEquals(LicenseName.url("CC-BY-SA-3.0"), Some("https://creativecommons.org/licenses/by-sa/3.0/"))
    assertEquals(LicenseName.label("CC-BY-NC-SA-4.0"), "CC BY-NC-SA 4.0")
    assertEquals(LicenseName.url("cc-by-4.0"), Some("https://creativecommons.org/licenses/by/4.0/"))
    assertEquals(LicenseName.label("CC BY-SA 2.5"), "CC BY-SA 2.5")
  }

  test("a ported licence keeps its jurisdiction; a bare CC-BY has no version and so no page") {
    assertEquals(LicenseName.label("CC-BY-SA-3.0-DE"), "CC BY-SA 3.0 DE")
    assertEquals(LicenseName.url("CC-BY-SA-3.0-DE"), Some("https://creativecommons.org/licenses/by-sa/3.0/de/"))
    assertEquals(LicenseName.label("CC-BY"), "CC BY")
    assertEquals(LicenseName.url("CC-BY"), None)
  }

  test("CC0, the Public Domain Mark and the public domain") {
    assertEquals(LicenseName.label("CC0"), "CC0 1.0")
    assertEquals(LicenseName.label("CC0-1.0"), "CC0 1.0")
    assertEquals(LicenseName.url("CC0-1.0"), Some("https://creativecommons.org/publicdomain/zero/1.0/"))
    assertEquals(LicenseName.label("PDM-1.0"), "Public Domain Mark 1.0")
    assertEquals(LicenseName.label("public-domain"), "Public domain")
    assertEquals(LicenseName.url("public-domain"), None)
  }

  test("an id it does not know is shown as stored, unlinked") {
    assertEquals(LicenseName.label("Copyrighted free use"), "Copyrighted free use")
    assertEquals(LicenseName.url("Copyrighted free use"), None)
  }

  test("the record's own link wins; the HTML is the name linked, escaped") {
    assertEquals(LicenseName.link("CC-BY-4.0", Some("https://example.org/lic")), ("CC BY 4.0", Some("https://example.org/lic")))
    assertEquals(LicenseName.link("CC-BY-4.0", Some(" ")), ("CC BY 4.0", Some("https://creativecommons.org/licenses/by/4.0/")))
    assertEquals(LicenseName.html("CC0", None),
      """<a class="media-license" href="https://creativecommons.org/publicdomain/zero/1.0/" target="_blank" rel="noopener license">CC0 1.0</a>""")
    assertEquals(LicenseName.html("<b>odd</b>", None), """<span class="media-license">&lt;b&gt;odd&lt;/b&gt;</span>""")
  }
