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

  test("images come from the web, this server or an inline raster only: no mail, no other data"):
    assertEquals(Href.image("https://x.org/a.png").map(_.address), Some("https://x.org/a.png"))
    assertEquals(Href.image("/source/x/assets/a.png").map(_.address), Some("/source/x/assets/a.png"))
    assertEquals(Href.image("mailto:a@b.org"), None)
    assertEquals(Href.image("javascript:x"), None)

  test("an inline image is a base64 png, jpeg, gif or webp; the type is read case-insensitively"):
    List(
      "data:image/png;base64,iVBORw0KGgo=",
      "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
      "data:image/gif;base64,R0lGODlhAQABAAAAACw=",
      "data:image/webp;base64,UklGRhYAAABXRUJQ",
      "DATA:IMAGE/PNG;BASE64,AAAA",
      "data:image/png;base64,AAAA"
    ).foreach(u => assertEquals(Href.image(u).map(_.address), Some(u), u))
    assertEquals(Href.image(" data:image/png;base64,AA\nAA ").map(_.address), Some("data:image/png;base64,AAAA"))

  test("every other inline image is refused: SVG, other types, not base64, parameters, stray characters"):
    List(
      "data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+",
      "data:image/svg+xml,<svg onload=alert(1)>",
      "data:IMAGE/SVG+XML;base64,PHN2Zz4=",
      "data:image/png,<svg onload=alert(1)>",
      "data:image/png;charset=utf-8;base64,AAAA",
      "data:image/png;base64,",
      "data:image/png;base64,AA AA",
      "data:image/png;base64,AA%3CAA",
      "data:image/png;base64,AAAA===",
      "data:image/png;base64,AAAA\"onerror=x",
      "data:image/jpg;base64,AAAA",
      "data:image/bmp;base64,AAAA",
      "data:image/x-icon;base64,AAAA",
      "data:text/html;base64,PHNjcmlwdD4=",
      "data:application/octet-stream;base64,AAAA",
      "data:;base64,AAAA",
      "data:,x"
    ).foreach(u => assertEquals(Href.image(u), None, u))

  test("an inline raster is an image, never a link"):
    assertEquals(Href.link("data:image/png;base64,AAAA"), None)
