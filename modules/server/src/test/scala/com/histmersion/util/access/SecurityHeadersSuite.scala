package com.histmersion
package util.access

/** The browser's safety headers built from the tile templates the settings name: the tile servers the policy lets the
  * page connect to and no other; the image directive pinned open (its hosts are no setting). Which templates the
  * settings name: `UiConfigViewsSuite`; the headers on the wire and their rebuild on a reload:
  * `SecurityHeadersHttpSuite`.
  */
class SecurityHeadersSuite extends munit.FunSuite:
  private def directive(policy: String, name: String): String =
    policy.split("; ").find(_.startsWith(name + " ")).getOrElse(fail(s"no $name in $policy"))
  private def policyOf(tiles: List[String]): String = SecurityHeaders.of(tiles).toMap.apply(SecurityHeaders.Policy)

  private val muted = "https://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
  private val mutedOrigins =
    List("https://a.tile.openstreetmap.fr", "https://b.tile.openstreetmap.fr", "https://c.tile.openstreetmap.fr")

  test("a tile template's origins: scheme and host, lower-case, {s} as the page expands it"):
    assertEquals(
      SecurityHeaders.origins("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"),
      List("https://a.tile.openstreetmap.org", "https://b.tile.openstreetmap.org", "https://c.tile.openstreetmap.org")
    )
    assertEquals(
      SecurityHeaders.origins(" HTTP://Tiles.Example.TEST:8080/{z}/{x}/{y}"),
      List("http://tiles.example.test:8080")
    )
    assertEquals(
      SecurityHeaders.origins("https://tile-{s}.example.test/x"),
      List("https://tile-a.example.test", "https://tile-b.example.test", "https://tile-c.example.test")
    )
    assertEquals(SecurityHeaders.origins("https://127.0.0.1:9000/t/{z}"), List("https://127.0.0.1:9000"))

  test("no origin for an address on this server, another scheme, or an authority that is no host and port"):
    List(
      "/tiles/{z}/{x}/{y}.png",
      "",
      "ftp://tiles.example.test/{z}",
      "data:image/png;base64,AAAA",
      "https://user:pw@tiles.example.test/{z}",
      "https://{z}.example.test/x",
      "https://tiles.example.test;script-src *",
      "https://tiles.example.test 'unsafe-inline'/x",
      "https://*.example.test/x",
      "https:///x"
    ).foreach(t => assertEquals(SecurityHeaders.origins(t), Nil, t))

  test("connect-src lists this server and the templates' tile servers, each once, and no other host"):
    assertEquals(
      directive(policyOf(List("https://tiles.example.test/{z}/{x}/{y}.png", muted)), "connect-src"),
      ("connect-src 'self'" :: "https://tiles.example.test" :: mutedOrigins).mkString(" ")
    )
    assertEquals(
      directive(policyOf(List(mutedOrigins.head + "/hot/{z}/{x}/{y}.png", muted)), "connect-src"),
      ("connect-src 'self'" :: mutedOrigins).mkString(" ")
    )
    assertEquals(directive(policyOf(List("/tiles/{z}")), "connect-src"), "connect-src 'self'")
    assertEquals(directive(policyOf(Nil), "connect-src"), "connect-src 'self'")
    val connect = directive(policyOf(List("https://tiles.example.test/{z}")), "connect-src")
    assert(!connect.contains("other.example.test"), connect)
    assert(!connect.matches(".*\\s(https?:|\\*)(\\s.*|$)"), s"no scheme-wide or wildcard source: $connect")
    assertEquals(
      directive(SecurityHeaders.contentSecurityPolicy(List("https://x.example.test")), "connect-src"),
      "connect-src 'self' https://x.example.test"
    )

  test("img-src stays open to any web address (thumbnails and stored images come from hosts no setting lists)"):
    // pinned: a media provider's thumbnail, and an image in a stored summary, note or full text, come from any host
    List(List("https://tiles.example.test/{z}"), Nil).foreach { tiles =>
      assertEquals(directive(policyOf(tiles), "img-src"), "img-src 'self' data: blob: https: http:")
    }

  test("the policy's fixed directives and the other two headers"):
    val headers = SecurityHeaders.of(List(muted)).toMap
    val policy = headers(SecurityHeaders.Policy)
    List(
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self' 'unsafe-inline'",
      "font-src 'self' data:",
      "worker-src 'self' blob:",
      "frame-src 'none'",
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'"
    ).foreach(d => assert(policy.split("; ").contains(d), s"$d in $policy"))
    assertEquals(headers("X-Content-Type-Options"), "nosniff")
    assertEquals(headers("X-Frame-Options"), "DENY")
