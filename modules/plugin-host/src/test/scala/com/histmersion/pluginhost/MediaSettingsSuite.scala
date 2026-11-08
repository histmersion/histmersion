package com.histmersion
package pluginhost

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.domain.Licence
import com.histmersion.domain.Licence.Family.*
import com.histmersion.plugin.LicenceReader

class MediaSettingsSuite extends munit.FunSuite:

  private def family(w: Licence.Family.Wire) = com.histmersion.plugin.Licence.Read.Family(w.value)

  test("media config: an honest User-Agent with no e-mail, the default allowlist without NC"):
    val m = AppConfig.load(org.ekrich.config.ConfigFactory.empty).media
    assertEquals(m.userAgent, "Histmersion (+https://github.com/histmersion/histmersion)")
    assert(!m.userAgent.value.contains("@") && !m.userAgent.value.toLowerCase.contains("mailto"))
    assertEquals(Licence.allowList(Licence.AllowList(m.commons.licences.value)), Set(Cc0, PublicDomain, CcBy, CcBySa))

  test("the licence reading a plugin is lent is the host's own"):
    assertEquals(
      HostLicences.readName(LicenceReader.ReadNameName("CC BY-SA 3.0 de")),
      com.histmersion.plugin.Licence.Read(
        com.histmersion.plugin.Licence.Read.Id("CC-BY-SA-3.0-DE"),
        family(CcBySa.wire),
        com.histmersion.plugin.Licence.Read.NeedsAttribution(true)
      )
    )
    assertEquals(
      HostLicences.readName(LicenceReader.ReadNameName("cc0")),
      com.histmersion.plugin.Licence.Read(
        com.histmersion.plugin.Licence.Read.Id("CC0-1.0"),
        family(Cc0.wire),
        com.histmersion.plugin.Licence.Read.NeedsAttribution(false)
      )
    )
    assertEquals(
      HostLicences.readUrl(LicenceReader.ReadUrlUrl("https://creativecommons.org/licenses/by/4.0/")),
      com.histmersion.plugin.Licence.Read(
        com.histmersion.plugin.Licence.Read.Id("CC-BY-4.0"),
        family(CcBy.wire),
        com.histmersion.plugin.Licence.Read.NeedsAttribution(true)
      )
    )
    assertEquals(
      HostLicences.readUrl(LicenceReader.ReadUrlUrl("http://rightsstatements.org/vocab/NoC-NC/1.0/")).family.value,
      Unknown.wire.value
    )
    assertEquals(
      HostLicences.readFirst(
        List(LicenceReader.ReadFirstStatements("nonsense"), LicenceReader.ReadFirstStatements("CC BY 4.0"))
      ),
      com.histmersion.plugin.Licence.Read(
        com.histmersion.plugin.Licence.Read.Id("CC-BY-4.0"),
        family(CcBy.wire),
        com.histmersion.plugin.Licence.Read.NeedsAttribution(true)
      )
    )
    assertEquals(
      HostLicences.readFirst(Nil),
      com.histmersion.plugin.Licence.Read(
        com.histmersion.plugin.Licence.Read.Id(""),
        family(Unknown.wire),
        com.histmersion.plugin.Licence.Read.NeedsAttribution(false)
      )
    )
    assertEquals(
      HostLicences.readFirst(List(LicenceReader.ReadFirstStatements(" "), LicenceReader.ReadFirstStatements("odd"))).id,
      "odd"
    )
