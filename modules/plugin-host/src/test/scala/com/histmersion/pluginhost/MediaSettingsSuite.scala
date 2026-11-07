package com.histmersion.pluginhost

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.domain.Licence
import com.histmersion.domain.Licence.Family.*

class MediaSettingsSuite extends munit.FunSuite:

  test("media config: an honest User-Agent with no e-mail, the default allowlist without NC"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.userAgent, "Histmersion (+https://github.com/histmersion/histmersion)")
    assert(!m.userAgent.contains("@") && !m.userAgent.toLowerCase.contains("mailto"))
    assertEquals(Licence.allowList(m.commons.licences), Set(Cc0, PublicDomain, CcBy, CcBySa))

  test("the licence reading a plugin is lent is the host's own"):
    assertEquals(
      HostLicences.readName(com.histmersion.plugin.LicenceReader.ReadNameName("CC BY-SA 3.0 de")),
      com.histmersion.plugin.Licence.Read(com.histmersion.plugin.Licence.Read.Id("CC-BY-SA-3.0-DE"), CcBySa.wire, com.histmersion.plugin.Licence.Read.NeedsAttribution(true))
    )
    assertEquals(HostLicences.readName(com.histmersion.plugin.LicenceReader.ReadNameName("cc0")), com.histmersion.plugin.Licence.Read(com.histmersion.plugin.Licence.Read.Id("CC0-1.0"), Cc0.wire, com.histmersion.plugin.Licence.Read.NeedsAttribution(false)))
    assertEquals(
      HostLicences.readUrl(com.histmersion.plugin.LicenceReader.ReadUrlUrl("https://creativecommons.org/licenses/by/4.0/")),
      com.histmersion.plugin.Licence.Read(com.histmersion.plugin.Licence.Read.Id("CC-BY-4.0"), CcBy.wire, com.histmersion.plugin.Licence.Read.NeedsAttribution(true))
    )
    assertEquals(HostLicences.readUrl(com.histmersion.plugin.LicenceReader.ReadUrlUrl("http://rightsstatements.org/vocab/NoC-NC/1.0/")).family, Unknown.wire)
    assertEquals(
      HostLicences.readFirst(List(com.histmersion.plugin.LicenceReader.ReadFirstStatements("nonsense"), com.histmersion.plugin.LicenceReader.ReadFirstStatements("CC BY 4.0"))),
      com.histmersion.plugin.Licence.Read(com.histmersion.plugin.Licence.Read.Id("CC-BY-4.0"), CcBy.wire, com.histmersion.plugin.Licence.Read.NeedsAttribution(true))
    )
    assertEquals(HostLicences.readFirst(Nil), com.histmersion.plugin.Licence.Read(com.histmersion.plugin.Licence.Read.Id(""), Unknown.wire, com.histmersion.plugin.Licence.Read.NeedsAttribution(false)))
    assertEquals(HostLicences.readFirst(List(com.histmersion.plugin.LicenceReader.ReadFirstStatements(" "), com.histmersion.plugin.LicenceReader.ReadFirstStatements("odd"))).id, "odd")
