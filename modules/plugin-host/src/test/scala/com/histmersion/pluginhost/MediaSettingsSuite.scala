package com.histmersion.pluginhost

import com.histmersion.config.AppConfig
import com.histmersion.domain.Licence
import com.histmersion.domain.LicenceFamily.*
import com.histmersion.plugin.LicenceRead

class MediaSettingsSuite extends munit.FunSuite:

  test("media config: an honest User-Agent with no e-mail, the default allowlist without NC"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.userAgent, "Histmersion (+https://github.com/histmersion/histmersion)")
    assert(!m.userAgent.contains("@") && !m.userAgent.toLowerCase.contains("mailto"))
    assertEquals(Licence.allowList(m.commons.licences), Set(Cc0, PublicDomain, CcBy, CcBySa))

  test("the licence reading a plugin is lent is the host's own"):
    assertEquals(HostLicences.readName("CC BY-SA 3.0 de"), LicenceRead("CC-BY-SA-3.0-DE", CcBySa.wire, true))
    assertEquals(HostLicences.readName("cc0"), LicenceRead("CC0-1.0", Cc0.wire, false))
    assertEquals(
      HostLicences.readUrl("https://creativecommons.org/licenses/by/4.0/"),
      LicenceRead("CC-BY-4.0", CcBy.wire, true)
    )
    assertEquals(HostLicences.readUrl("http://rightsstatements.org/vocab/NoC-NC/1.0/").family, Unknown.wire)
    assertEquals(HostLicences.readFirst(List("nonsense", "CC BY 4.0")), LicenceRead("CC-BY-4.0", CcBy.wire, true))
    assertEquals(HostLicences.readFirst(Nil), LicenceRead("", Unknown.wire, false))
    assertEquals(HostLicences.readFirst(List(" ", "odd")).id, "odd")
