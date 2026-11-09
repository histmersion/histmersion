package com.histmersion
package plugin.testkit.kit

import com.histmersion.util.problem.{Legacy, Problem}
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
  val info =
    Plugin.Info(Plugin.Info.Id("kit-fetcher"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitFetcherConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    c.token
      .fold[Either[Problem, Option[Secret.Resolved]]](Right(None))(t => ctx.secret(t).map(Some(_)))
      .flatMap { key =>
        ctx.sourceFetchers
          .add(
            Registry.AddId("kit-fetcher"),
            new SourceFetcher:
              val access = Access.Open
              def fetch(work: WorkKey, fc: Fetch.Context): Fetch =
                work.doi match
                  case None      => Fetch.Miss(Problem.Unclassified(Legacy.Text("no DOI")))
                  case Some(doi) =>
                    val url = s"${c.baseUrl.stripSuffix("/")}/$doi.pdf" + key.fold("")(k => s"?key=$k")
                    ctx.http.get(Plugin.Http.Url(url)) match
                      case Left(Plugin.Http.Failure.Status(code, why)) if code.refusesAccess => Fetch.Refused(why)
                      case Left(why)                                                         => Fetch.Miss(why.problem)
                      case Right(bytes)                                                      =>
                        fc.log.info(Plugin.Log.Line(s"$doi: licence CC-BY-4.0"))
                        Fetch.Found(
                          FetchedFile(
                            FetchedFile.Source("kit-fetcher"),
                            Some(FetchedFile.Url(url)),
                            FetchedFile.Bytes(bytes.value),
                            FetchedFile.Extension("pdf"),
                            Some(Licence("CC-BY-4.0"))
                          )
                        )
          )
      }

final case class KitBaseConfig(baseUrl: String) derives ConfigReader

/** A media collection: `<base-url>/search?q=<label>` answers one record per line, `key|licence|image`. */
final class KitMediaPlugin extends Plugin:
  type Config = KitBaseConfig
  val info = Plugin.Info(Plugin.Info.Id("kit-media"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitBaseConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.mediaCollections
      .add(
        Registry.AddId("kit-media"),
        new MediaCollection:
          val title = MediaCollection.Title("Kit pictures")
          val role = MediaCollection.Image
          override def requestIntervalMs = MediaCollection.RequestIntervalMs(0)
          def search(place: MediaPlace, http: Plugin.Http) =
            http
              .get(Plugin.Http.Url(s"${c.baseUrl}/search?q=${place.label}"))
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
                    case _ =>
                      Left(
                        Plugin.Http.Failure.Transport(Problem.Unclassified(Legacy.Text(s"unreadable record '$line'")))
                      )
                }
              )
      )

/** An event catalogue: part `p1` at `<base-url>/p1` pages on to `p2`; each line `id|label|year`. */
final class KitEventsPlugin extends Plugin:
  type Config = KitBaseConfig
  val info =
    Plugin.Info(Plugin.Info.Id("kit-events"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitBaseConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.eventCatalogues
      .add(
        Registry.AddId("kit-events"),
        new EventCatalogue:
          val title = EventCatalogue.Title("Kit events"); val licence = Licence("CC0");
          val citation = EventCatalogue.Citation("Kit"); val home = EventCatalogue.Home(c.baseUrl)
          override def requestIntervalMs = EventCatalogue.RequestIntervalMs(0)
          def parts(q: CatalogueQuery) = List(EventCatalogue.ReadPart("p1"))
          def read(part: EventCatalogue.ReadPart, q: CatalogueQuery, http: Plugin.Http) =
            http
              .get(Plugin.Http.Url(s"${c.baseUrl}/$part"))
              .left
              .map(_.problem)
              .map { b =>
                val events =
                  new String(b.value, UTF_8).linesIterator.toList.map(_.split('|')).collect {
                    case Array(id, label, from, to) =>
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
      )

final case class KitAiConfig(baseUrl: String, apiKey: Option[Secret]) derives ConfigReader

/** An AI provider: one POST to `<base-url>/chat` per call, its body the answer; `check` wants a key. */
final class KitAiPlugin extends Plugin:
  type Config = KitAiConfig
  val info = Plugin.Info(Plugin.Info.Id("kit-ai"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitAiConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    c.apiKey
      .fold[Either[Problem, Option[Secret.Resolved]]](Right(None))(k => ctx.secret(k).map(Some(_)))
      .flatMap { key =>
        ctx.aiProviders
          .add(
            Registry.AddId("kit-ai"),
            new AiProvider:
              val title = AiProvider.Title("Kit AI")
              override def check(model: AiProvider.CheckModel) =
                key.map(_ => ()).toRight(Problem.Unclassified(Legacy.Text("no api-key configured")))
              def turn(r: AiTurn.Request, http: AiHttp) =
                http
                  .post(
                    Plugin.Http.Url(s"${c.baseUrl}/chat"),
                    Map(Header.Name("x-api-key") -> Header.Value(key.fold("")(_.value))),
                    AiHttp.PostBody(r.model.value),
                    AiHttp.TimeoutSeconds(30)
                  )
                  .left
                  .map(e => AiFailure.Error(e.reported))
                  .map(resp => AiTurn(AiTurn.Text(resp.body.value)))
          )
      }

/** @param entries `ref=value` each */
final case class KitVaultConfig(entries: List[String]) derives ConfigReader

/** A secret vault over its config's entries. */
final class KitVaultPlugin extends Plugin:
  type Config = KitVaultConfig
  val info = Plugin.Info(Plugin.Info.Id("kit-vault"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitVaultConfig, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.secretVaults
      .add(
        Registry.AddId("kit-vault"),
        new Secret.Vault:
          val name = Secret.Vault.Name("kit-vault")
          def resolve(ref: Secret.Vault.ResolveRef) =
            c.entries
              .map(_.split("=", 2))
              .collectFirst { case Array(r, v) if r == ref.value => Secret.Resolved(v) }
              .toRight(Problem.Unclassified(Legacy.Text(s"no entry '$ref'")))
      )

/** A plugin whose `register` refuses. */
final class KitBrokenPlugin extends Plugin:
  type Config = KitBaseConfigOrNothing
  val info =
    Plugin.Info(Plugin.Info.Id("kit-broken"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitBaseConfigOrNothing, ctx: Plugin.Context): Either[Problem, Unit] = Left(
    Problem.Unclassified(Legacy.Text("broken on purpose"))
  )

final case class KitBaseConfigOrNothing() derives ConfigReader

/** A plugin shipped without its defaults resource. */
final class KitNoDefaultsPlugin extends Plugin:
  type Config = KitBaseConfigOrNothing
  val info =
    Plugin.Info(Plugin.Info.Id("kit-nodefaults"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
  def register(c: KitBaseConfigOrNothing, ctx: Plugin.Context): Either[Problem, Unit] = Right(())
