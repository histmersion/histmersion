package com.histmersion.server.media

import com.histmersion.pluginhost.{HttpTransport, Polite, figureRules}
import com.histmersion.files.*

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.server.ConverterClient
import io.scalaland.chimney.dsl.*

import javax.sql.DataSource
import com.histmersion.values.MediaRole

/** A source's picture on its hover card (#1446): a book's COVER — the local Calibre library's own
  * (the CLI reads it host-side and uploads it, [[storeCover]]) or the Open Library Covers API's by
  * ISBN/OCLC ([[openLibrary]]) — and a paper's ILLUSTRATION: one of the figures its conversion
  * already stored, chosen by [[FigurePick]] ([[illustrate]]). No model is involved anywhere.
  *
  * Thumbnails come from the OCR sidecar ([[ConverterClient.thumbnail]]): the native server has no
  * image codec that works (ImageIO hangs under native-image on macOS). Without one, the card shows
  * the picture's own bytes. */
final class SourcePictures(ds: DataSource, cfg: => MediaConfig, http: HttpTransport, converter: ConverterClient):

  private def rules = cfg.illustrations.figureRules

  /** The thumbnail of `bytes`, or `None` (logged) when the sidecar makes none. */
  private def thumbnail(bytes: Array[Byte], name: String): Option[ConverterClient.Thumb] =
    converter.thumbnail(bytes, name, cfg.thumbPx) match
      case Right(t) if t.bytes.nonEmpty => Some(t)
      case Right(_)                     => None
      case Left(why) =>
        scribe.info(s"media: no thumbnail for $name ($why) — the card shows the picture itself")
        None

  // ── illustrations ─────────────────────────────────────────────────────────────────────────────

  /** Choose (and unless `dryRun`, link) a source's illustration. `Right(line)` when it has one now,
    * `Left(reason)` when none of its figures fits. A source that already has this very figure keeps it. */
  def illustrate(citekey: String, dryRun: Boolean): Either[String, MediaSourceLine] =
    val figs = MediaRepo.figures(ds, citekey)
    if figs.isEmpty then Left("no figures")
    else
      val weighed = FigurePick.withRefs(figs.map(SourcePictures.toFigure), MediaRepo.body(ds, citekey))
      FigurePick.choose(weighed, rules) match
        case None => Left("no figure fit to illustrate it")
        case Some(pick) =>
          val row  = figs.find(_.name == pick.name).get
          val line = MediaSourceLine(citekey, MediaRole.Illustration, MediaProvider.Source, pick.name)
          if !dryRun then
            if !row.hasThumb then
              MediaRepo.bytesOf(ds, row.uid).flatMap((_, b) => thumbnail(b, row.name)).foreach(t =>
                MediaRepo.setThumb(ds, row.uid, t.bytes, t.mime, Some(t.width).filter(_ > 0), Some(t.height).filter(_ > 0)))
            MediaRepo.setSourcePicture(ds, citekey, row.uid, MediaRole.Illustration, None)
          Right(line)

  // ── covers ────────────────────────────────────────────────────────────────────────────────────

  /** A cover from the Open Library Covers API, by the source's ISBN (else OCLC number). */
  def openLibrary(keys: MediaRepo.CoverKeys, dryRun: Boolean, fetch: Polite): Either[String, MediaSourceLine] =
    fetch.synchronized(openLibraryNow(keys, dryRun, fetch))

  private def openLibraryNow(keys: MediaRepo.CoverKeys, dryRun: Boolean, fetch: Polite): Either[String, MediaSourceLine] =
    OpenLibrary.key(keys.isbn, keys.oclc) match
      case None => Left("no usable ISBN or OCLC number")
      case Some((kind, id)) =>
        val line = MediaSourceLine(keys.citekey, MediaRole.Cover, MediaProvider.OpenLibrary, s"$kind:$id")
        if dryRun then Right(line)
        else
          val c   = cfg.covers
          val url = OpenLibrary.coverUrl(c.openLibraryUrl, kind, id, c.size)
          val got = fetch.file(url, "image/jpeg") match
            case Left(why) if why.contains("HTTP 404") => Left("Open Library has no cover")
            case Left(why)                             => Left(s"Open Library: $why")
            case Right((bytes, _)) if bytes.length > cfg.maxBytes => Left(s"cover of ${bytes.length} bytes is larger than media.max-bytes")
            case Right((bytes, mime)) if !mime.startsWith("image/") => Left(s"Open Library answered $mime, not an image")
            case Right((bytes, mime)) =>
              val thumb = fetch.file(OpenLibrary.coverUrl(c.openLibraryUrl, kind, id, c.thumbSize), mime).toOption
                .filter((b, m) => b.nonEmpty && m.startsWith("image/"))
              val size  = ImageSize.of(bytes)
              val tsize = thumb.flatMap((b, _) => ImageSize.of(b))
              val a = MediaRepo.ProviderAsset(MediaProvider.OpenLibrary, s"$kind:$id", s"cover-$kind-$id.jpg", mime, bytes,
                size.map(_._1), size.map(_._2), thumb.map(_._1), thumb.map(_._2), tsize.map(_._1), tsize.map(_._2),
                license = "", licenseUrl = None, author = None, credit = "Cover: Open Library",
                sourceUrl = OpenLibrary.pageUrl(c.openLibraryPage, kind, id),
                originalUrl = Some(url.stripSuffix("?default=false")), kind = Some(AssetKind.Photo))
              val (uid, _) = MediaRepo.save(ds, a, (MediaRepo.Source, keys.citekey))
              MediaRepo.setSourcePicture(ds, keys.citekey, uid, MediaRole.Cover, Some(kind))
              Right(line)
          MediaRepo.checked(ds, MediaProvider.OpenLibrary, MediaRepo.Source, keys.citekey, if got.isRight then 1 else 0)
          got

  /** The polite client every Open Library call goes through — ONE per server, so its own, slower pace
    * holds across runs and the OCR jobs' covers alike (calls are serialised on it). */
  lazy val openLibraryClient: Polite = Polite(http, cfg.copy(requestIntervalMs = cfg.covers.requestIntervalMs))

  /** Pure: an image's type from its first bytes (`None` unless JPEG, PNG, GIF or WebP). */
  private[media] def sniff(b: Array[Byte]): Option[String] =
    def at(i: Int) = if b.length > i then b(i) & 0xff else -1
    if at(0) == 0xff && at(1) == 0xd8 then Some("image/jpeg")
    else if at(0) == 0x89 && at(1) == 'P' && at(2) == 'N' && at(3) == 'G' then Some("image/png")
    else if at(0) == 'G' && at(1) == 'I' && at(2) == 'F' then Some("image/gif")
    else if b.length >= 12 && new String(b.slice(8, 12), "US-ASCII") == "WEBP" then Some("image/webp")
    else None

  /** Store `bytes` (read from the local Calibre library by the CLI) as the source's cover. */
  def storeCover(p: SourceCoverParams, bytes: Array[Byte]): Either[ApiError, MediaItem] =
    val bad = (m: String) => Left(ApiError(m, Some(Write.CodeInvalid)))
    if p.provider != MediaProvider.Calibre then bad(s"a cover is uploaded from ${MediaProvider.Calibre} only (Open Library's come through `media covers`)")
    else if p.key.trim.isEmpty then bad("the cover's key (its Calibre id) is missing")
    else if bytes.isEmpty then bad("the cover is empty")
    else if bytes.length > cfg.maxBytes then bad(s"the cover is ${bytes.length} bytes, more than media.max-bytes")
    else sniff(bytes) match
      case None => bad("the cover is not a JPEG, PNG, GIF or WebP image")
      case Some(mime) =>
        MediaRepo.sourceKey(ds, p.citekey.trim) match
          case None => Left(ApiError(s"No source '${p.citekey}'", Some(Write.CodeNotFound)))
          case Some(ck) =>
            val key   = p.key.trim
            val size  = ImageSize.of(bytes)
            val thumb = thumbnail(bytes, s"cover-$key")
            val a = MediaRepo.ProviderAsset(MediaProvider.Calibre, key, MediaText.safeName(s"cover-calibre-$key.${mime.drop(6)}"),
              mime, bytes, size.map(_._1), size.map(_._2), thumb.map(_.bytes), thumb.map(_.mime),
              thumb.map(_.width).filter(_ > 0), thumb.map(_.height).filter(_ > 0),
              license = "", licenseUrl = None, author = None, credit = "Cover: the local Calibre library",
              sourceUrl = s"calibre:book/$key", originalUrl = None, kind = Some(AssetKind.Photo))
            val (uid, _) = MediaRepo.save(ds, a, (MediaRepo.Source, ck))
            MediaRepo.setSourcePicture(ds, ck, uid, MediaRole.Cover, Some(MediaProvider.Calibre))
            MediaRepo.show(ds, uid).toRight(ApiError(s"cover of '$ck' not stored"))

  // ── a run over the corpus ─────────────────────────────────────────────────────────────────────

  /** Covers (Open Library) and illustrations for the sources a request names; `progress` hears how
    * far it got. A dry run reads the database only. */
  def run(req: MediaCoversRequest, progress: String => Unit = _ => ()): MediaCoversReport =
    val only  = req.citekey.map(_.trim).filter(_.nonEmpty).map(c => MediaRepo.sourceKey(ds, c).getOrElse(c))
    val limit = req.limit.filter(_ > 0).getOrElse(Int.MaxValue)
    val skipped = List.newBuilder[String]
    val failed  = List.newBuilder[String]
    val lines   = List.newBuilder[MediaSourceLine]
    // `var`: the sources looked at so far
    var seen = Set.empty[String]

    if req.covers then
      val hinted = for ck <- only; i <- req.isbn.map(_.trim).filter(_.nonEmpty) yield MediaRepo.CoverKeys(ck, Some(i), None)
      val cands = MediaRepo.coverCandidates(ds, only, req.refresh, limit) match
        case Nil => hinted.filter(k => req.refresh || MediaRepo.sourcePicture(ds, k.citekey, MediaRole.Cover).isEmpty).toList
        case cs  => cs
      val fetch = openLibraryClient
      cands.zipWithIndex.foreach { (k, i) =>
        seen += k.citekey
        progress(s"Open Library covers: ${i + 1}/${cands.size} (${k.citekey})")
        openLibrary(k, req.dryRun, fetch) match
          case Right(l) => lines += l
          case Left(why) if why.startsWith("Open Library:") => failed += s"${k.citekey}: $why"
          case Left(why) => skipped += s"cover: $why"
      }

    if req.illustrations then
      val cands = MediaRepo.illustrationCandidates(ds, only, req.refresh, limit)
      cands.zipWithIndex.foreach { (ck, i) =>
        seen += ck
        if i % 10 == 0 then progress(s"illustrations: ${i + 1}/${cands.size}")
        try
          illustrate(ck, req.dryRun) match
            case Right(l)  => lines += l
            case Left(why) => skipped += s"illustration: $why"
        catch
          case e: InterruptedException => throw e
          case e: Exception => failed += s"$ck: ${e.getClass.getSimpleName}: ${e.getMessage}"
      }

    val ls = lines.result()
    MediaCoversReport(req.dryRun, seen.size, ls.count(_.role == MediaRole.Cover), ls.count(_.role == MediaRole.Illustration),
      ls, MediaCount.of(skipped.result()), failed.result())

  /** After a conversion stored a source's figures: its illustration, and — when its record holds an
    * ISBN or OCLC number and it has no cover yet — Open Library's cover. Best-effort: a note for the
    * job's progress line, never a failure. */
  def afterIngest(citekey: String): Option[String] =
    try
      val ill = illustrate(citekey, dryRun = false).toOption.map(l => s"illustration ${l.detail}")
      val cover = MediaRepo.coverCandidates(ds, Some(citekey), refresh = false, 1).headOption
        .flatMap(k => openLibrary(k, dryRun = false, openLibraryClient).toOption).map(l => s"cover ${l.detail}")
      Option(List(ill, cover).flatten).filter(_.nonEmpty).map(_.mkString(" · "))
    catch
      case e: InterruptedException => throw e
      case e: Exception =>
        scribe.warn(s"media: no illustration for $citekey: ${e.getMessage}")
        None

object SourcePictures:
  /** Pure: a figure row as [[FigurePick]] weighs it, before the text's links to it are counted. */
  def toFigure(f: MediaRepo.SourceFigure): FigurePick.Figure =
    f.into[FigurePick.Figure].enableDefaultValues.transform
