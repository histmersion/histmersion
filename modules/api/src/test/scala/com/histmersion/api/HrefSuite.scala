package com.histmersion
package api

class HrefSuite extends munit.FunSuite:

  test("web, mail and relative addresses are links"):
    List(
      "https://doi.org/10.1/x",
      "http://example.org",
      "HTTPS://EXAMPLE.ORG",
      "mailto:a@b.org",
      "/source/x",
      "#cite-1",
      "?q=x",
      "assets/f.png",
      "//cdn.example.org/x",
      ""
    ).foreach { u =>
      assertEquals(Href.link(u).map(_.address), Some(u), u)
    }

  test("script, data and every other scheme are refused"):
    List(
      "javascript:alert(1)",
      "JavaScript:alert(1)",
      "vbscript:msgbox(1)",
      "data:text/html,x",
      "file:///etc/passwd",
      "ftp://x",
      "doi:10.1/x",
      "c:/Users/x.png"
    ).foreach(u => assertEquals(Href.link(u), None, u))

  test("a scheme split by tabs or line breaks, or behind spaces and control characters, is read as a browser reads it"):
    List(
      "java\tscript:alert(1)",
      "java\nscript:alert(1)",
      "java\r\nscript:x",
      " \u0000javascript:x",
      "\u0001javascript:x "
    )
      .foreach(u => assertEquals(Href.link(u), None, u.replace("\u0000", "\\0")))
    assertEquals(Href.link("  https://x.org/a\tb  ").map(_.address), Some("https://x.org/ab"))

  test("a colon after no scheme is a relative address"):
    List("1javascript:x", "java script:x", "/a:b", "a/b:c", "\u00a0javascript:x").foreach { u =>
      assertEquals(Href.link(u).map(_.address), Some(u.trim), u)
    }

  test("images come from the web or this server only: no mail, no data"):
    assertEquals(Href.image("https://x.org/a.png").map(_.address), Some("https://x.org/a.png"))
    assertEquals(Href.image("/source/x/assets/a.png").map(_.address), Some("/source/x/assets/a.png"))
    assertEquals(Href.image("data:image/png;base64,AAAA"), None)
    assertEquals(Href.image("mailto:a@b.org"), None)
    assertEquals(Href.image("javascript:x"), None)
