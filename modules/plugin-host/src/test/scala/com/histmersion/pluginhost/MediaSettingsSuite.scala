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

  test("media config: max-year 0 sets no latest year; the exclusion lists are trimmed comma lists"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals((m.met.latestYear, m.europeana.latestYear, m.smithsonian.latestYear), (None, Some(1500), Some(1500)))
    assertEquals(MediaSettings.items(" OpenUp! , ,Fixture Aggregator,"), List("OpenUp!", "Fixture Aggregator"))
    assertEquals(MediaSettings.items(""), Nil)
    val ex = m.europeana.europeanaExclusions
    assertEquals(ex.providers, List("OpenUp!"))
    assert(ex.dataProviders.contains("Natural History Museum") && ex.types.contains("PreservedSpecimen"), ex.toString)
    assert(
      m.smithsonian.excludedProviders.contains("NMNHINV") && !m.smithsonian.excludedProviders.contains("NMNHANTHRO")
    )
    assertEquals(
      (m.met.excludedProviders, m.met.europeanaExclusions),
      (Nil, com.histmersion.domain.Europeana.Exclusions.none)
    )
