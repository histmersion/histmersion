package com.histmersion
package util.access

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.{AppConfig, UiSettings}

/** The browser's safety headers built from the settings: the tile servers the policy lets the page connect to, read
  * from `ui.tiles` and the Muted map, none when the base map is local; the image directive pinned open (its hosts are
  * no setting). The headers on the wire and their rebuild on a reload: `SecurityHeadersHttpSuite`.
  */
class SecurityHeadersSuite extends munit.FunSuite:
  private val ui = AppConfig.load(org.ekrich.config.ConfigFactory.empty).ui
  private def withTiles(tiles: String, source: String = "auto"): UiSettings =
    ui.modify(_.tiles).setTo(UiSettings.Tiles(tiles)).modify(_.baseMapSource).setTo(UiSettings.BaseMapSource(source))
  private def directive(policy: String, name: String): String =
    policy.split("; ").find(_.startsWith(name + " ")).getOrElse(fail(s"no $name in $policy"))

  private val muted =
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

  test("the page may connect to this server, ui.tiles and the Muted map; the base map's source decides"):
    assertEquals(
      SecurityHeaders.tileOrigins(withTiles("https://tiles.example.test/{z}/{x}/{y}.png")),
      "https://tiles.example.test" :: muted
    )
    assertEquals(
      SecurityHeaders.tileOrigins(withTiles("https://tiles.example.test/{z}", "online")),
      "https://tiles.example.test" :: muted
    )
    assertEquals(SecurityHeaders.tileOrigins(withTiles(muted.head + "/hot/{z}/{x}/{y}.png")), muted)
    assertEquals(SecurityHeaders.tileOrigins(withTiles("/tiles/{z}")), muted)
    assertEquals(SecurityHeaders.tileOrigins(withTiles("https://tiles.example.test/{z}", "local")), Nil)
    assertEquals(SecurityHeaders.tileOrigins(withTiles("https://tiles.example.test/{z}", " LOCAL ")), Nil)

  test("connect-src lists exactly those; any other host is not let in"):
    val policy = SecurityHeaders.contentSecurityPolicy(List("https://tiles.example.test"))
    assertEquals(directive(policy, "connect-src"), "connect-src 'self' https://tiles.example.test")
    assertEquals(directive(SecurityHeaders.contentSecurityPolicy(Nil), "connect-src"), "connect-src 'self'")
    val headers = SecurityHeaders.of(withTiles("https://tiles.example.test/{z}")).toMap
    val connect = directive(headers(SecurityHeaders.Policy), "connect-src")
    assert(connect.contains("https://tiles.example.test") && !connect.contains("other.example.test"), connect)
    assert(!connect.matches(".*\\s(https?:|\\*)(\\s.*|$)"), s"no scheme-wide or wildcard source: $connect")

  test("img-src stays open to any web address (thumbnails and stored images come from hosts no setting lists)"):
    // pinned: a media provider's thumbnail, and an image in a stored summary, note or full text, come from any host
    List(withTiles("https://tiles.example.test/{z}"), withTiles("https://tiles.example.test/{z}", "local")).foreach {
      u =>
        val policy = SecurityHeaders.of(u).toMap.apply(SecurityHeaders.Policy)
        assertEquals(directive(policy, "img-src"), "img-src 'self' data: blob: https: http:")
    }

  test("the policy's fixed directives and the other two headers"):
    val headers = SecurityHeaders.of(ui).toMap
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
