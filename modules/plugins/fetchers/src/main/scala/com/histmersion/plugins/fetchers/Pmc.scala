package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{FetchContext, FetchedFile, PluginHttp, SourceFetcher}
import com.histmersion.upstreams.{
  json,
  text,
  AcquireParse,
  EuropePmcHit,
  Jats,
  PmcObject,
  PmcRecord,
  UpstreamSettings,
  Url
}

/** The `pmc` plugin's two fetchers, by DOI: `pmc`, the PDF — Europe PMC's free links, else the one NCBI's PMC Article
  * Datasets publish — and `pmc-text`, the record's JATS full text when there is no PDF anywhere (the chain runs it
  * after every PDF source). Both read one Europe PMC search and one dataset record per work, shared through the fetch's
  * memo.
  */
final class Pmc(cfg: UpstreamSettings, client: () => PluginHttp):

  private def http: PluginHttp = client()

  /** The PMC hosts get the project's public identity — no contact e-mail, no `email` parameter. */
  private def pmcHeaders = Map("User-Agent" -> cfg.publicUserAgent)

  /** Everything PMC can offer for one DOI, asked lazily and at most once per fetch.
    *
    *   1. Europe PMC's REST search names the PMCID and the PDF links it lists as free (`OA`/`F`); those are tried first
    *      (europepmc.org may answer with a bot-protection challenge, which is no copy).
    *   2. NCBI's PMC Article Datasets record for that PMCID names the PDF it publishes (the open-access subset and most
    *      author manuscripts), verified against the record's MD5.
    *   3. With no PDF anywhere, the record's JATS XML — the article's own full text — is the copy; the ingest renders
    *      it to Markdown instead of OCR.
    */
  final private class PmcLookup(doi: String, ctx: FetchContext):
    lazy val hit: Option[EuropePmcHit] =
      http.json(
        Url(
          cfg.europepmc.url,
          "/search",
          "query" -> s"""DOI:"$doi"""",
          "resultType" -> "core",
          "format" -> "json",
          "pageSize" -> "5"
        ),
        pmcHeaders
      ) match
        case Right(j) => AcquireParse.europepmcHit(j, doi)
        case Left(e)  =>
          ctx.log.info(s"europepmc search failed ($e)")
          None

    lazy val record: Option[PmcRecord] =
      hit.flatMap(_.pmcid).flatMap { id =>
        http
          .text(Url(cfg.pmcDataset.url, "/", "list-type" -> "2", "prefix" -> s"metadata/$id."), pmcHeaders)
          .left
          .map(e => ctx.log.info(s"pmc dataset listing failed ($e)"))
          .toOption
          .flatMap(AcquireParse.pmcLatestVersion(_, id))
          .flatMap(v =>
            http
              .json(s"${cfg.pmcDataset.url}/metadata/$id.$v.json", pmcHeaders)
              .left
              .map(e => ctx.log.info(s"pmc dataset record failed ($e)"))
              .toOption
          )
          .flatMap(AcquireParse.pmcRecord)
      }

    def pdf: Option[FetchedFile] =
      hit.toList
        .flatMap(_.pdfUrls)
        .iterator
        .map(u => Downloads(http).pdf(ctx.log, "europepmc", u, pmcHeaders))
        .collectFirst { case Some(f) => f }
        .orElse(record.flatMap(_.pdf).flatMap(o => dataset(o, "pdf")(Downloads.looksLikePdf)))

    def fullText: Option[FetchedFile] =
      record.flatMap(_.xml).flatMap(o => dataset(o, "xml")(b => Jats.isJats(String(b, "UTF-8"))))

    private def dataset(o: PmcObject, ext: String)(ok: Array[Byte] => Boolean): Option[FetchedFile] =
      val url = s"${cfg.pmcDataset.url}/${o.key}"
      http.get(url, pmcHeaders) match
        case Left(e) =>
          ctx.log.info(s"pmc fetch failed ($e)"); None
        case Right(b) if !ok(b) =>
          ctx.log.info(s"pmc: $url returned ${b.length} bytes that are not a $ext — skipping"); None
        case Right(b) if o.md5.exists(m => Downloads.md5(b).exists(_ != m)) =>
          ctx.log.warn(s"pmc: $url does not match the MD5 its record gives — skipping"); None
        case Right(b) => Some(FetchedFile("pmc", Some(url), b, ext))

  private def lookup(ctx: FetchContext, doi: String): PmcLookup =
    ctx.memo(s"pmc:lookup:$doi")(PmcLookup(doi, ctx))

  val pdfStep: SourceFetcher = ByDoi("pmc", (ctx, d) => lookup(ctx, d).pdf)
  val textStep: SourceFetcher = ByDoi("pmc-text", (ctx, d) => lookup(ctx, d).fullText)
