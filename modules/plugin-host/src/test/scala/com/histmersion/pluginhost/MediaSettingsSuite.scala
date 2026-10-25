package com.histmersion.pluginhost

import com.histmersion.config.AppConfig
import com.histmersion.domain.MediaLicence.*
import com.histmersion.values.MediaRole

class MediaSettingsSuite extends munit.FunSuite:

  test("media config: an honest User-Agent with no e-mail, the default allowlist without NC, P15/P3311 roles"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.userAgent, "Histmersion (+https://github.com/histmersion/histmersion)")
    assert(!m.userAgent.contains("@") && !m.userAgent.toLowerCase.contains("mailto"))
    assertEquals(m.commons.allowed, Set(Cc0, PublicDomain, CcBy, CcBySa))
    assertEquals(m.wikidata.placeRoles.toMap.get("P3311"), Some(MediaRole.Plan))
    assertEquals(m.wikidata.placeRoles.toMap.get("P15"), Some(MediaRole.RouteMap))
    assertEquals(m.wikidata.eventRoles, List("P18" -> MediaRole.Image))
