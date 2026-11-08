package com.histmersion
package pluginhost

import com.histmersion.config.{ConfigReload, HotPlan}
import com.histmersion.plugin.Plugin
import org.ekrich.config.{Config, ConfigFactory}

import scala.util.Try

/** The host settings a first-party plugin reads: the paths of Histmersion's own configuration (`histmersion.acquire`,
  * `histmersion.events.gvp`, …) the host supplies it, each under the same path in the plugin's settings without the
  * `histmersion.` root (`histmersion.acquire.unpaywall.url` → `acquire.unpaywall.url`). The user keeps writing those
  * keys and their `HISTMERSION_*` variables; the plugin's report shows only its own block.
  *
  * ONE table drives both the settings and the reload rule: a first-party plugin is rebuilt when its own block or one of
  * its paths changes. A plugin the table does not name — a third party's, a vault — is rebuilt when its block changes
  * or a key that shapes the HTTP client every plugin is lent ([[FirstPartySettings.ClientKeys]]) does.
  *
  * @param plugin
  *   the first-party plugin's id
  * @param paths
  *   the host paths it reads; a `histmersion.plugins.<id>` path reads another first-party plugin's setting as that
  *   plugin's effective block has it
  */
final case class FirstPartySettings(plugin: Plugin.Info.Id, paths: List[ConfigReload.Key]):

  /** Whether a change of the host key `changed` (not this plugin's own block) concerns this plugin. */
  def readsHostKey(changed: ConfigReload.ChangedKey): Boolean = paths.exists(HotPlan.keyMatches(changed, _))

  /** The settings the host supplies this plugin out of `host` (the `histmersion` tree, other plugins' blocks
    * effective), keys relative to the plugin's block.
    */
  def fallback(host: Config): Config =
    paths.foldLeft(ConfigFactory.empty) { (acc, path) =>
      Try(Option.when(host.hasPathOrNull(path.value))(host.getValue(path.value))).toOption.flatten
        .fold(acc)(v => acc.withValue(path.value.stripPrefix(FirstPartySettings.Root + "."), v))
    }

object FirstPartySettings:
  val Root = "histmersion"

  /** The keys that shape the acquisition client every plugin of a generation is lent (its User-Agent, timeouts and TLS
    * trust, its patience with `Retry-After`): a change rebuilds every plugin that uses the client.
    */
  val ClientKeys: List[ConfigReload.Key] =
    List(ConfigReload.Key("histmersion.acquire"), ConfigReload.Key("histmersion.media.max-retry-after-seconds"))

  private val fetcher = ClientKeys
  private val unpaywallContact = ConfigReload.Key(s"${PluginHost.Root}.oa-unpaywall.contact-email")

  private def entry(id: String, paths: List[ConfigReload.Key]): FirstPartySettings =
    FirstPartySettings(Plugin.Info.Id(id), paths)

  /** The media settings every media collection reads: its own block (`media.met.*`), the pace and widths, and the
    * Wikidata and Commons APIs the Commons-backed ones share.
    */
  private val Media = ConfigReload.Key("histmersion.media")

  /** The first-party plugins and what each reads of the host's settings. The command-line AI runtimes read their
    * settings at each use, from the host (until they move into plugins of their own): no path, and a host key never
    * rebuilds them.
    */
  val all: List[FirstPartySettings] =
    List("archive-org", "oa-unpaywall", "oa-core", "oa-openalex", "pmc", "arxiv").map(entry(_, fetcher)) ++
      List(
        // the landing step asks Unpaywall with oa-unpaywall's own contact, so the two share one request
        entry("oa-landing", fetcher :+ unpaywallContact),
        entry(
          "events-gvp",
          List(ConfigReload.Key("histmersion.events.gvp"), ConfigReload.Key("histmersion.events.min-year"))
        ),
        entry("events-noaa", List(ConfigReload.Key("histmersion.events.noaa"))),
        entry("events-wikidata", List(ConfigReload.Key("histmersion.events.wikidata")))
      ) ++
      List(
        "media-wikidata",
        "media-depicts",
        "media-met",
        "media-cleveland",
        "media-aic",
        "media-loc",
        "media-pleiades",
        "media-opencontext",
        "media-smithsonian",
        "media-europeana"
      ).map(entry(_, List(Media))) ++
      List(
        "ai-claude-code",
        "ai-codex",
        "ai-opencode"
      ).map(entry(_, Nil)) ++
      // the gazetteers read no host setting: the host lends each download its own client (places.fetch-*)
      List("gazetteer-cliopatria", "gazetteer-awmc", "gazetteer-chronontology").map(entry(_, Nil)) ++
      // each HTTP AI provider its own `agents.providers.<name>` (base URL, key, strict tools, timeout)
      List("anthropic", "openai", "local").map(n =>
        entry(s"ai-$n", List(ConfigReload.Key(s"histmersion.agents.providers.$n")))
      ) ++
      // the Calibre library's settings are its own block's (the CLI's `cli.calibre.*` are the CLI's)
      List(entry("calibre", Nil))

  private val byId: Map[Plugin.Info.Id, FirstPartySettings] = all.map(s => s.plugin -> s).toMap

  def of(plugin: Plugin.Info.Id): Option[FirstPartySettings] = byId.get(plugin)

  /** Every host path some first-party plugin reads, and the client's keys: the keys a plugin generation is built from,
    * besides `histmersion.plugins`.
    */
  def hostKeys: List[ConfigReload.Key] =
    (ClientKeys ++ all.flatMap(_.paths)).distinct.filterNot(k =>
      HotPlan.keyMatches(ConfigReload.ChangedKey(k.value), ConfigReload.Key(PluginHost.Root))
    )

  /** Whether a change of the host key `changed` rebuilds `plugin`: one of its paths for a first-party plugin, a client
    * key for any other.
    */
  def rebuilds(plugin: Plugin.Info.Id, changed: ConfigReload.ChangedKey): Boolean =
    of(plugin).fold(ClientKeys.exists(HotPlan.keyMatches(changed, _)))(_.readsHostKey(changed))

  /** The host settings first-party plugins are supplied from: `merged` (the resolved `histmersion` tree) with each
    * first-party plugin's block as it is in effect for `plugins` (its user block over its defaults, the environment's
    * overrides applied) — what a `histmersion.plugins.<id>` path reads.
    */
  def hostView(merged: Config, user: Config, env: Config, plugins: List[Plugin]): Config =
    plugins.filter(p => byId.contains(p.info.id)).foldLeft(merged) { (view, p) =>
      val id = p.info.id
      val effective = PluginHost.defaults(p).flatMap(d => PluginHost.block(user, d, env, id))
      effective.fold(_ => view, b => view.withValue(s"${PluginHost.Root}.$id", b.root))
    }

  /** The settings `plugin` is supplied from `host` (empty for a plugin the table does not name). */
  def fallback(plugin: Plugin.Info.Id, host: Config): Config = of(plugin).fold(ConfigFactory.empty)(_.fallback(host))
