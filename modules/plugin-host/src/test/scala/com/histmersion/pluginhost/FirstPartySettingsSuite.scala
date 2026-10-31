package com.histmersion.pluginhost

import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigFactory

/** The one table of host settings the first-party plugins read: what each is supplied, and which changed keys rebuild
  * it.
  */
class FirstPartySettingsSuite extends munit.FunSuite:

  private val host = ConfigFactory.parseString(
    """histmersion {
      |  acquire { unpaywall.url = "https://u.example", contact-email = "host@example.org" }
      |  media.max-retry-after-seconds = 60
      |  events { min-year = -12000, gvp { url = "https://gvp.example", floor-year = -9700 }, noaa.url = "https://n.example" }
      |  plugins.oa-unpaywall { enabled = true, contact-email = "plugin@example.org" }
      |}""".stripMargin
  )

  test("a fetcher is supplied histmersion.acquire, under acquire, and nothing it does not read"):
    val f = FirstPartySettings.fallback("arxiv", host)
    assertEquals(f.getString("acquire.unpaywall.url"), "https://u.example")
    assertEquals(f.getInt("media.max-retry-after-seconds"), 60)
    assert(!f.hasPath("events"), f.toString)

  test("the landing step is supplied oa-unpaywall's contact; an event catalogue its own block and the years"):
    assertEquals(
      FirstPartySettings.fallback("oa-landing", host).getString("plugins.oa-unpaywall.contact-email"),
      "plugin@example.org"
    )
    val gvp = FirstPartySettings.fallback("events-gvp", host)
    assertEquals((gvp.getString("events.gvp.url"), gvp.getInt("events.min-year")), ("https://gvp.example", -12000))
    assert(!gvp.hasPath("events.noaa") && !gvp.hasPath("acquire"), gvp.toString)

  test("a plugin the table does not name — a third party's — is supplied nothing"):
    assert(FirstPartySettings.fallback("zq-portal", host).isEmpty)
    assert(FirstPartySettings.fallback("media-met", host).isEmpty)

  test("a changed host key rebuilds the plugins that read it; a third party's on the client's keys alone"):
    assert(FirstPartySettings.rebuilds("pmc", "histmersion.acquire.europepmc.url"))
    assert(FirstPartySettings.rebuilds("pmc", "histmersion.media.max-retry-after-seconds"))
    assert(!FirstPartySettings.rebuilds("pmc", "histmersion.events.gvp.url"))
    assert(FirstPartySettings.rebuilds("events-gvp", "histmersion.events.gvp.url"))
    assert(FirstPartySettings.rebuilds("events-gvp", "histmersion.events"))
    assert(!FirstPartySettings.rebuilds("events-gvp", "histmersion.acquire.user-agent"))
    assert(!FirstPartySettings.rebuilds("media-met", "histmersion.acquire.user-agent"))
    assert(FirstPartySettings.rebuilds("zq-portal", "histmersion.acquire.user-agent"))
    assert(!FirstPartySettings.rebuilds("zq-portal", "histmersion.events.gvp.url"))
    assert(FirstPartySettings.of("oa-landing").exists(_.readsHostKey("histmersion.plugins.oa-unpaywall.contact-email")))
    assert(!FirstPartySettings.of("oa-landing").exists(_.readsHostKey("histmersion.plugins.oa-unpaywall.enabled")))

  test("the host keys a generation is built from: the client's and every path the table names, no plugin block"):
    val keys = FirstPartySettings.hostKeys
    assert(keys.contains("histmersion.acquire") && keys.contains("histmersion.events.wikidata"), keys.toString)
    assert(!keys.exists(_.startsWith(PluginHost.Root)), keys.toString)

  final private case class Contact(contactEmail: Option[String] = None) derives ConfigReader

  /** A first-party plugin whose defaults declare an environment override, as `oa-unpaywall`'s do. */
  final private class Unpaywall extends Plugin:
    type Config = Contact
    val info = PluginInfo("oa-unpaywall", "1.0.0", Spi.Version)
    override def defaultsResource = "zq-first-defaults.conf"
    def register(c: Contact, ctx: PluginContext) = Right(())

  test("another plugin's block is read as it is in effect: its defaults, the user's block, the environment"):
    val merged = ConfigFactory.parseString("histmersion.acquire.contact-email = \"\"")
    val user = ConfigFactory.parseString("histmersion.plugins.oa-unpaywall.contact-email = \"mine@example.org\"")
    val view = FirstPartySettings.hostView(merged, user, ConfigFactory.empty, List(Unpaywall()))
    assertEquals(view.getString("histmersion.plugins.oa-unpaywall.contact-email"), "mine@example.org")
    val env = ConfigFactory.parseString("ZQ_FIRST_CONTACT = \"env@example.org\"")
    val fromEnv = FirstPartySettings.hostView(merged, ConfigFactory.empty, env, List(Unpaywall()))
    assertEquals(fromEnv.getString("histmersion.plugins.oa-unpaywall.contact-email"), "env@example.org")
