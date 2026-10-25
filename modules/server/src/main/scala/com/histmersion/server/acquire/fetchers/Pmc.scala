package com.histmersion.server.acquire.fetchers

import com.histmersion.pluginhost.{FetchChain, Url}

import com.histmersion.config.AcquireConfig
import com.histmersion.pluginhost.{AcquireParse, EuropePmcHit, PmcObject, PmcRecord}
import com.histmersion.domain.Jats
import com.histmersion.plugin.{Fetch, FetchContext, SourceFetcher, WorkKey}
import com.histmersion.server.acquire.{FetchedFile, Http, OaFetch}

/** The `pmc` plugin's two fetchers, by DOI (#1523): `pmc`, the PDF — Europe PMC's free links, else the
  * one NCBI's PMC Article Datasets publish — and `pmc-text`, the record's JATS full text when there is
  * no PDF anywhere (the chain runs it after every PDF source). Both read one Europe PMC search and one
  * dataset record per work, shared through the fetch's memo. */
final class Pmc(cfg: AcquireConfig, http: () => Http):
  import Fetching.*

  /** The PMC hosts get the project's public identity — no contact e-mail, no `email` parameter. */
  private def pmcHeaders = Map(Http.UserAgent -> cfg.publicUserAgent)

  /** Everything PMC can offer for one DOI, asked lazily and at most once per fetch.
    *
    * 1. Europe PMC's REST search names the PMCID and the PDF links it lists as free (`OA`/`F`); those
    *    are tried first (europepmc.org may answer with a bot-protection challenge, which is no copy).
    * 2. NCBI's PMC Article Datasets record for that PMCID names the PDF it publishes (the open-access
    *    subset and most author manuscripts), verified against the record's MD5.
    * 3. With no PDF anywhere, the record's JATS XML — the article's own full text — is the copy; the
    *    ingest job renders it to Markdown ([[com.histmersion.server.JatsConverter]]) instead of OCR. */
  private final class PmcLookup(doi: String):
    lazy val hit: Option[EuropePmcHit] =
      http().json(Url(cfg.europepmc.url, "/search",
        "query" -> s"""DOI:"$doi"""", "resultType" -> "core", "format" -> "json", "pageSize" -> "5"),
        pmcHeaders) match
        case Right(j) => AcquireParse.europepmcHit(j, doi)
        case Left(e) =>
          scribe.info(s"acquire: europepmc search failed ($e)")
          None

    lazy val record: Option[PmcRecord] =
      hit.flatMap(_.pmcid).flatMap { id =>
        http().text(Url(cfg.pmcDataset.url, "/", "list-type" -> "2", "prefix" -> s"metadata/$id."), pmcHeaders)
          .left.map(e => scribe.info(s"acquire: pmc dataset listing failed ($e)")).toOption
          .flatMap(AcquireParse.pmcLatestVersion(_, id))
          .flatMap(v => http().json(s"${cfg.pmcDataset.url}/metadata/$id.$v.json", pmcHeaders)
            .left.map(e => scribe.info(s"acquire: pmc dataset record failed ($e)")).toOption)
          .flatMap(AcquireParse.pmcRecord)
      }

    def pdf: Option[FetchedFile] =
      hit.toList.flatMap(_.pdfUrls).iterator.map(u => tryUrl(http(), "europepmc", u, pmcHeaders))
        .collectFirst { case Some(f) => f }
        .orElse(record.flatMap(_.pdf).flatMap(o => dataset(o, "pdf")(FetchChain.looksLikePdf)))

    def fullText: Option[FetchedFile] =
      record.flatMap(_.xml).flatMap(o => dataset(o, "xml")(b => Jats.isJats(new String(b, "UTF-8"))))

    private def dataset(o: PmcObject, ext: String)(ok: Array[Byte] => Boolean): Option[FetchedFile] =
      val url = s"${cfg.pmcDataset.url}/${o.key}"
      http().bytes(url, pmcHeaders) match
        case Left(e) =>
          scribe.info(s"acquire: pmc fetch failed ($e)"); None
        case Right(b) if !ok(b) =>
          scribe.info(s"acquire: pmc: $url returned ${b.length} bytes that are not a $ext — skipping"); None
        case Right(b) if o.md5.exists(m => OaFetch.md5(b).exists(_ != m)) =>
          scribe.warn(s"acquire: pmc: $url does not match the MD5 its record gives — skipping"); None
        case Right(b) => Some(FetchedFile("pmc", Some(url), b, ext))

  private def lookup(ctx: FetchContext, doi: String): PmcLookup =
    ctx.memo(s"pmc:lookup:$doi")(PmcLookup(doi))

  private def byDoi(step: String)(f: PmcLookup => Option[FetchedFile]): SourceFetcher =
    open((work: WorkKey, ctx: FetchContext) =>
      work.doi.fold[Fetch](Fetch.Miss(s"$step needs a DOI"))(d => answer(f(lookup(ctx, d)), s"$step has no copy of $d")))

  val pdf: SourceFetcher  = byDoi("pmc")(_.pdf)
  val text: SourceFetcher = byDoi("pmc-text")(_.fullText)
