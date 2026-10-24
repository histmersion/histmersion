package com.histmersion.plugin.testkit.kit

import com.histmersion.plugin.*
import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

import java.nio.charset.StandardCharsets.UTF_8

// Test plugins, one per extension point, found by ServiceLoader on the test class path as a plugin
// author's own plugin is in its tests.

final case class KitFetcherConfig(baseUrl: String, token: Option[Secret]) derives ConfigReader

/** A fetcher: a work's DOI → `<base-url>/<doi>.pdf` (with `?key=<token>` when one is configured). A bot
  * challenge is a refusal; a copy found is logged with its licence. */
final class KitFetcherPlugin extends Plugin:
  type Config = KitFetcherConfig
  val info = PluginInfo("kit-fetcher", "0.1.0", Spi.Version)
  def register(c: KitFetcherConfig, ctx: PluginContext): Either[String, Unit] =
    c.token.fold[Either[String, Option[String]]](Right(None))(t => ctx.secret(t).map(Some(_))).flatMap { key =>
      ctx.sourceFetchers.add("kit-fetcher", new SourceFetcher:
        val access = Access.Open
        def fetch(work: WorkKey, fc: FetchContext): Fetch =
          work.doi match
            case None => Fetch.Miss("no DOI")
            case Some(doi) =>
              val url = s"${c.baseUrl.stripSuffix("/")}/$doi.pdf" + key.fold("")(k => s"?key=$k")
              ctx.http.get(url) match
                case Left(why) if why.contains("challenge") => Fetch.Refused(why)
                case Left(why) => Fetch.Miss(why)
                case Right(bytes) =>
                  fc.log.info(s"$doi: licence CC-BY-4.0")
                  Fetch.Found(FetchedFile("kit-fetcher", Some(url), bytes, "pdf", Some("CC-BY-4.0")))
      )
    }

final case class KitBaseConfig(baseUrl: String) derives ConfigReader

/** A media collection: `<base-url>/search?q=<label>` answers one record per line, `key|licence|image`. */
final class KitMediaPlugin extends Plugin:
  type Config = KitBaseConfig
  val info = PluginInfo("kit-media", "0.1.0", Spi.Version)
  def register(c: KitBaseConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.mediaCollections.add("kit-media", new MediaCollection:
      val title = "Kit pictures"
      val role  = MediaCollection.Image
      override def requestIntervalMs = 0
      def search(place: MediaPlace, http: PluginHttp) =
        http.get(s"${c.baseUrl}/search?q=${place.label}").map(b => new String(b, UTF_8).linesIterator.toList.map { line =>
          line.split("\\|", -1) match
            case Array(key, licence, image) =>
              Right(MediaItem(key, s"Picture $key", s"${c.baseUrl}/$key", Option(image).filter(_.nonEmpty),
                Right(MediaRights(licence, credit = "A. Photographer"))))
            case _ => Left(s"unreadable record '$line'")
        }))

/** An event catalogue: part `p1` at `<base-url>/p1` pages on to `p2`; each line `id|label|year`. */
final class KitEventsPlugin extends Plugin:
  type Config = KitBaseConfig
  val info = PluginInfo("kit-events", "0.1.0", Spi.Version)
  def register(c: KitBaseConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.eventCatalogues.add("kit-events", new EventCatalogue:
      val title = "Kit events"; val licence = "CC0"; val citation = "Kit"; val home = c.baseUrl
      override def requestIntervalMs = 0
      def parts(q: CatalogueQuery) = List("p1")
      def read(part: String, q: CatalogueQuery, http: PluginHttp) =
        http.get(s"${c.baseUrl}/$part").map { b =>
          val events = new String(b, UTF_8).linesIterator.toList.map(_.split('|')).collect {
            case Array(id, label, from, to) =>
              CatalogueEvent(id, s"${c.baseUrl}/$id", label, "eruption", Some(from.toLong), Some(to.toLong))
          }
          CataloguePage(events, Option.when(part == "p1")("p2"))
        })

final case class KitAiConfig(baseUrl: String, apiKey: Option[Secret]) derives ConfigReader

/** An AI provider: one POST to `<base-url>/chat` per call, its body the answer; `check` wants a key. */
final class KitAiPlugin extends Plugin:
  type Config = KitAiConfig
  val info = PluginInfo("kit-ai", "0.1.0", Spi.Version)
  def register(c: KitAiConfig, ctx: PluginContext): Either[String, Unit] =
    c.apiKey.fold[Either[String, Option[String]]](Right(None))(k => ctx.secret(k).map(Some(_))).flatMap { key =>
      ctx.aiProviders.add("kit-ai", new AiProvider:
        val title = "Kit AI"
        override def check(model: String) = key.map(_ => ()).toRight("no api-key configured")
        def turn(r: AiTurnRequest, http: AiHttp) =
          http.post(s"${c.baseUrl}/chat", Map("x-api-key" -> key.getOrElse("")), r.model, 30)
            .left.map(AiFailure.Error(_)).map(resp => AiTurn(resp.body)))
    }

/** @param entries `ref=value` each */
final case class KitVaultConfig(entries: List[String]) derives ConfigReader

/** A secret vault over its config's entries. */
final class KitVaultPlugin extends Plugin:
  type Config = KitVaultConfig
  val info = PluginInfo("kit-vault", "0.1.0", Spi.Version)
  def register(c: KitVaultConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.secretVaults.add("kit-vault", new SecretVault:
      val name = "kit-vault"
      def resolve(ref: String) = c.entries.map(_.split("=", 2)).collectFirst { case Array(`ref`, v) => v }.toRight(s"no entry '$ref'"))

/** A plugin whose `register` refuses. */
final class KitBrokenPlugin extends Plugin:
  type Config = KitBaseConfigOrNothing
  val info = PluginInfo("kit-broken", "0.1.0", Spi.Version)
  def register(c: KitBaseConfigOrNothing, ctx: PluginContext): Either[String, Unit] = Left("broken on purpose")

final case class KitBaseConfigOrNothing() derives ConfigReader

/** A plugin shipped without its defaults resource. */
final class KitNoDefaultsPlugin extends Plugin:
  type Config = KitBaseConfigOrNothing
  val info = PluginInfo("kit-nodefaults", "0.1.0", Spi.Version)
  def register(c: KitBaseConfigOrNothing, ctx: PluginContext): Either[String, Unit] = Right(())
