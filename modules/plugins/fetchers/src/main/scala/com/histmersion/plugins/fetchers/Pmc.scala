package com.histmersion
package plugins.fetchers

import com.histmersion.util.problem.Text.sentence
import com.histmersion.plugin.Header

import com.histmersion.plugin.{FetchedFile, SourceFetcher}
import com.histmersion.upstreams.http.{json, text}
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.europepmc.EuropePmcHit
import com.histmersion.upstreams.jats.Jats
import com.histmersion.upstreams.pmc.{PmcObject, PmcRecord}
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.plugin.Fetch
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Doi
import com.histmersion.lint.{LintRule, tolerated}

/** The `pmc` plugin's two fetchers, by DOI: `pmc`, the PDF — Europe PMC's free links, else the one NCBI's PMC Article
  * Datasets publish — and `pmc-text`, the record's JATS full text when there is no PDF anywhere (the chain runs it
  * after every PDF source). Both read one Europe PMC search and one dataset record per work, shared through the fetch's
  * memo.
  */
final class Pmc(cfg: UpstreamSettings, client: () => Plugin.Http):

  private def http: Plugin.Http = client()

  /** The PMC hosts get the project's public identity — no contact e-mail, no `email` parameter. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pmcHeaders: Map[String, String] = Map("User-Agent" -> cfg.publicUserAgent.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pmcGetHeaders: Map[Header.Name, Header.Value] =
    pmcHeaders.map((k, v) => Header.Name(k) -> Header.Value(v))

  /** Everything PMC can offer for one DOI, asked lazily and at most once per fetch.
    *
    *   1. Europe PMC's REST search names the PMCID and the PDF links it lists as free (`OA`/`F`); those are tried first
    *      (europepmc.org may answer with a bot-protection challenge, which is no copy).
    *   2. NCBI's PMC Article Datasets record for that PMCID names the PDF it publishes (the open-access subset and most
    *      author manuscripts), verified against the record's MD5.
    *   3. With no PDF anywhere, the record's JATS XML — the article's own full text — is the copy; the ingest renders
    *      it to Markdown instead of OCR.
    */
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final private class PmcLookup(doi: Doi, ctx: Fetch.Context):
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    lazy val hit: Option[EuropePmcHit] =
      http.json(
        Url(
          RepositoryLanding.Candidate.Url(cfg.europepmc.url.value),
          Url.UrlPath("/search"),
          "query" -> s"""DOI:"$doi"""",
          "resultType" -> "core",
          "format" -> "json",
          "pageSize" -> "5"
        ).value,
        pmcHeaders
      ) match
        case Right(j) => AcquireParse.europepmcHit(j, doi)
        case Left(e)  =>
          ctx.log.info(Plugin.Log.Line(s"europepmc search failed ($e)"))
          None

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    lazy val record: Option[PmcRecord] =
      hit.flatMap(_.pmcid).flatMap { id =>
        http
          .text(
            Url(
              RepositoryLanding.Candidate.Url(cfg.pmcDataset.url.value),
              Url.UrlPath("/"),
              "list-type" -> "2",
              "prefix" -> s"metadata/$id."
            ).value,
            pmcHeaders
          )
          .left
          .map(e => ctx.log.info(Plugin.Log.Line(s"pmc dataset listing failed ($e)")))
          .toOption
          .flatMap(x => AcquireParse.pmcLatestVersion(Jats.Xml(x), id))
          .flatMap(v =>
            http
              .json(s"${cfg.pmcDataset.url}/metadata/$id.$v.json", pmcHeaders)
              .left
              .map(e => ctx.log.info(Plugin.Log.Line(s"pmc dataset record failed ($e)")))
              .toOption
          )
          .flatMap(AcquireParse.pmcRecord)
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def pdf: Iterator[Fetch] =
      hit.toList
        .flatMap(_.pdfUrls)
        .iterator
        .map(u =>
          Downloads(http).pdf(ctx.log, FetchedFile.Source("europepmc"), Plugin.Http.Url(u.value), pmcGetHeaders)
        ) ++
        Iterator
          .single(())
          .flatMap(_ =>
            record.flatMap(_.pdf).flatMap(o => dataset(o, FetchedFile.Extension("pdf"))(Downloads.looksLikePdf))
          )
          .map(Fetch.Found(_))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fullText: Iterator[Fetch] =
      record
        .flatMap(_.xml)
        .flatMap(o => dataset(o, FetchedFile.Extension("xml"))(b => Jats.isJats(Jats.Xml(String(b.value, "UTF-8")))))
        .iterator
        .map(Fetch.Found(_))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def dataset(o: PmcObject, ext: FetchedFile.Extension)(
        ok: FetchedFile.Bytes => Boolean
    ): Option[FetchedFile] =
      val url = s"${cfg.pmcDataset.url}/${o.key}"
      http.get(Plugin.Http.Url(url), pmcGetHeaders).map(body => FetchedFile.Bytes(body.value)) match
        case Left(e) =>
          ctx.log.info(Plugin.Log.Line(s"pmc fetch failed (${e.sentence})")); None
        case Right(b) if !ok(b) =>
          ctx.log.info(
            Plugin.Log.Line(s"pmc: $url returned ${b.value.length} bytes that are not a $ext — skipping")
          );
          None
        case Right(b) if o.md5.exists(m => Downloads.md5(b).exists(_ != m)) =>
          ctx.log.warn(Plugin.Log.Line(s"pmc: $url does not match the MD5 its record gives — skipping")); None
        case Right(b) => Some(FetchedFile(FetchedFile.Source("pmc"), Some(FetchedFile.Url(url)), b, ext))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def lookup(ctx: Fetch.Context, doi: Doi): PmcLookup =
    ctx.memo(Fetch.Context.MemoKey(s"pmc:lookup:$doi"))(PmcLookup(doi, ctx))

  val pdfStep: SourceFetcher = ByDoi(ByDoi.Step("pmc"), (ctx, d) => lookup(ctx, d).pdf)
  val textStep: SourceFetcher = ByDoi(ByDoi.Step("pmc-text"), (ctx, d) => lookup(ctx, d).fullText)
