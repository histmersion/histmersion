package com.histmersion
package plugin.testkit.kit

import com.histmersion.plugin.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

import java.nio.charset.StandardCharsets.UTF_8

// Test plugins, one per extension point, found by ServiceLoader on the test class path as a plugin
// author's own plugin is in its tests.

final case class KitFetcherConfig(baseUrl: String, token: Option[Secret]) derives ConfigReader

/** A fetcher: a work's DOI → `<base-url>/<doi>.pdf` (with `?key=<token>` when one is configured). A bot challenge is a
  * refusal; a copy found is logged with its licence.
  */
final class KitFetcherPlugin extends Plugin:
  type Config = KitFetcherConfig
  val info = Plugin.Info(Plugin.Info.Id("kit-fetcher"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitFetcherConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    c.token
      .fold[Either[Plugin.Context.SecretResultPart1, Option[Plugin.Context.SecretResultPart2]]](Right(None))(t => ctx.secret(t).map(Some(_)))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
      .flatMap { key =>
      ctx.sourceFetchers.add(
        Registry.AddId("kit-fetcher"),
        new SourceFetcher:
          val access = Access.Open
          def fetch(work: WorkKey, fc: Fetch.Context): Fetch =
            work.doi match
              case None      => Fetch.Miss(Fetch.Miss.Why("no DOI"))
              case Some(doi) =>
                val url = s"${c.baseUrl.stripSuffix("/")}/$doi.pdf" + key.fold("")(k => s"?key=$k")
                ctx.http.get(Plugin.Http.GetUrl(url)) match
                  case Left(why) if why.value.contains("challenge") => Fetch.Refused(Fetch.Refused.Why(why.value))
                  case Left(why)                                    => Fetch.Miss(Fetch.Miss.Why(why.value))
                  case Right(bytes)                                 =>
                    fc.log.info(Plugin.Log.InfoMessage(s"$doi: licence CC-BY-4.0"))
                    Fetch.Found(
                      FetchedFile(
                        FetchedFile.Source("kit-fetcher"),
                        Some(FetchedFile.Url(url)),
                        FetchedFile.Bytes(bytes.value),
                        FetchedFile.Extension("pdf"),
                        Some(Licence("CC-BY-4.0"))
                      )
                    )
        ).left.map(e => Plugin.RegisterResultPart1(e.value))
    }

final case class KitBaseConfig(baseUrl: String) derives ConfigReader

/** A media collection: `<base-url>/search?q=<label>` answers one record per line, `key|licence|image`. */
final class KitMediaPlugin extends Plugin:
  type Config = KitBaseConfig
  val info = Plugin.Info(Plugin.Info.Id("kit-media"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitBaseConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.mediaCollections.add(
      Registry.AddId("kit-media"),
      new MediaCollection:
        val title = MediaCollection.TitleResult("Kit pictures")
        val role = MediaCollection.RoleResult(MediaCollection.Image)
        override def requestIntervalMs = MediaCollection.RequestIntervalMsResult(0)
        def search(place: MediaPlace, http: Plugin.Http) =
          http
            .get(Plugin.Http.GetUrl(s"${c.baseUrl}/search?q=${place.label}"))
            .left
            .map(e => MediaCollection.SearchResultPart1(e.value))
            .map(b =>
              new String(b.value, UTF_8).linesIterator.toList.map { line =>
                line.split("\\|", -1) match
                  case Array(key, licence, image) =>
                    Right(
                      MediaItem(
                        MediaItem.Key(key),
                        MediaItem.Title(s"Picture $key"),
                        MediaItem.PageUrl(s"${c.baseUrl}/$key"),
                        Option(image).filter(_.nonEmpty).map(MediaItem.ImageUrl(_)),
                        Right(MediaRights(Licence(licence), credit = MediaRights.Credit("A. Photographer")))
                      )
                    )
                  case _ => Left(MediaCollection.SearchResultPart2Part1(s"unreadable record '$line'"))
              }
            )
    ).left.map(e => Plugin.RegisterResultPart1(e.value))

/** An event catalogue: part `p1` at `<base-url>/p1` pages on to `p2`; each line `id|label|year`. */
final class KitEventsPlugin extends Plugin:
  type Config = KitBaseConfig
  val info = Plugin.Info(Plugin.Info.Id("kit-events"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitBaseConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.eventCatalogues.add(
      Registry.AddId("kit-events"),
      new EventCatalogue:
        val title = EventCatalogue.TitleResult("Kit events"); val licence = Licence("CC0"); val citation = EventCatalogue.CitationResult("Kit"); val home = EventCatalogue.HomeResult(c.baseUrl)
        override def requestIntervalMs = EventCatalogue.RequestIntervalMsResult(0)
        def parts(q: CatalogueQuery) = List(EventCatalogue.PartsResult("p1"))
        def read(part: EventCatalogue.ReadPart, q: CatalogueQuery, http: Plugin.Http) =
          http.get(Plugin.Http.GetUrl(s"${c.baseUrl}/$part")).left.map(e => EventCatalogue.ReadResultPart1(e.value)).map { b =>
            val events =
              new String(b.value, UTF_8).linesIterator.toList.map(_.split('|')).collect { case Array(id, label, from, to) =>
                CatalogueEvent(
                  CatalogueEvent.Id(id),
                  CatalogueEvent.Url(s"${c.baseUrl}/$id"),
                  CatalogueEvent.Label(label),
                  CatalogueEvent.Kind("eruption"),
                  Some(Year(from.toInt)),
                  Some(Year(to.toInt))
                )
              }
            CataloguePage(events, Option.when(part.value == "p1")(CataloguePage.Next("p2")))
          }
    ).left.map(e => Plugin.RegisterResultPart1(e.value))

final case class KitAiConfig(baseUrl: String, apiKey: Option[Secret]) derives ConfigReader

/** An AI provider: one POST to `<base-url>/chat` per call, its body the answer; `check` wants a key. */
final class KitAiPlugin extends Plugin:
  type Config = KitAiConfig
  val info = Plugin.Info(Plugin.Info.Id("kit-ai"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitAiConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    c.apiKey
      .fold[Either[Plugin.Context.SecretResultPart1, Option[Plugin.Context.SecretResultPart2]]](Right(None))(k => ctx.secret(k).map(Some(_)))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
      .flatMap { key =>
      ctx.aiProviders.add(
        Registry.AddId("kit-ai"),
        new AiProvider:
          val title = AiProvider.TitleResult("Kit AI")
          override def check(model: AiProvider.CheckModel) = key.map(_ => ()).toRight(AiProvider.CheckResultPart1("no api-key configured"))
          def turn(r: AiTurn.Request, http: AiHttp) =
            http
              .post(
                AiHttp.PostUrl(s"${c.baseUrl}/chat"),
                Map(AiHttp.PostHeadersPart1("x-api-key") -> AiHttp.PostHeadersPart2(key.fold("")(_.value))),
                AiHttp.PostBody(r.model.value),
                AiHttp.PostTimeoutSeconds(30)
              )
              .left
              .map(e => AiFailure.Error(AiFailure.Error.Message(e.value)))
              .map(resp => AiTurn(AiTurn.Text(resp.body.value)))
      ).left.map(e => Plugin.RegisterResultPart1(e.value))
    }

/** @param entries `ref=value` each */
final case class KitVaultConfig(entries: List[String]) derives ConfigReader

/** A secret vault over its config's entries. */
final class KitVaultPlugin extends Plugin:
  type Config = KitVaultConfig
  val info = Plugin.Info(Plugin.Info.Id("kit-vault"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitVaultConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.secretVaults.add(
      Registry.AddId("kit-vault"),
      new Secret.Vault:
        val name = Secret.Vault.NameResult("kit-vault")
        def resolve(ref: Secret.Vault.ResolveRef) =
          c.entries
            .map(_.split("=", 2))
            .collectFirst { case Array(r, v) if r == ref.value => Secret.Vault.ResolveResultPart2(v) }
            .toRight(Secret.Vault.ResolveResultPart1(s"no entry '$ref'"))
    ).left.map(e => Plugin.RegisterResultPart1(e.value))

/** A plugin whose `register` refuses. */
final class KitBrokenPlugin extends Plugin:
  type Config = KitBaseConfigOrNothing
  val info = Plugin.Info(Plugin.Info.Id("kit-broken"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitBaseConfigOrNothing, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] = Left(Plugin.RegisterResultPart1("broken on purpose"))

final case class KitBaseConfigOrNothing() derives ConfigReader

/** A plugin shipped without its defaults resource. */
final class KitNoDefaultsPlugin extends Plugin:
  type Config = KitBaseConfigOrNothing
  val info = Plugin.Info(Plugin.Info.Id("kit-nodefaults"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitBaseConfigOrNothing, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] = Right(())
