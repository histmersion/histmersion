package com.histmersion
package domain.media.covers
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.pluginhost.{figureRules, HttpTransport, Polite}
import com.histmersion.upstreams.media.MediaText

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.media.assets.{AssetService, PictureTarget, ProviderAsset, SourceFigure}
import com.histmersion.domain.media.figures.FigurePick
import com.histmersion.domain.media.imports.ImportService
import com.histmersion.util.ocr.ConverterClient
import io.scalaland.chimney.dsl.*

import com.histmersion.domain.media.MediaRole
import com.histmersion.api.media.{
  AssetKind,
  ImageSize,
  MediaCount,
  MediaCoversReport,
  MediaCoversRequest,
  MediaItem,
  MediaProvider,
  MediaSourceLine,
  SourceCoverParams
}
import com.histmersion.api.Api
import com.histmersion.api.jobs.JobInfo
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.media.MediaSources
import com.histmersion.util.ocr.Pixels
import com.histmersion.domain.library.Isbn
import com.histmersion.plugin.Plugin
import com.histmersion.domain.media.covers.SourcePictures.CoverRefusal
import com.histmersion.plugin.WorkKey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** A source's picture on its hover card (#1446): a book's COVER — the local Calibre library's own (the CLI reads it
  * host-side and uploads it, [[storeCover]]) or the Open Library Covers API's by ISBN/OCLC ([[openLibrary]]) — and a
  * paper's ILLUSTRATION: one of the figures its conversion already stored, chosen by [[FigurePick]] ([[illustrate]]).
  * No model is involved anywhere.
  *
  * Thumbnails come from the OCR sidecar ([[ConverterClient.thumbnail]]): the native server has no image codec that
  * works (ImageIO hangs under native-image on macOS). Without one, the card shows the picture's own bytes.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class SourcePictures(
    assets: AssetService,
    covers: CoverService,
    imports: ImportService,
    sources: MediaSources,
    config: () => MediaConfig,
    http: HttpTransport,
    converter: ConverterClient
):

  private def cfg: MediaConfig = config()

  private def rules = cfg.illustrations.figureRules

  /** The thumbnail of `bytes`, or `None` (logged) when the sidecar makes none. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def thumbnail(bytes: AssetService.Bytes, name: String): Option[ConverterClient.Thumb] =
    converter.thumbnail(
      ConverterClient.DocumentBytes(bytes.value),
      ConverterClient.Filename(name),
      Pixels(cfg.thumbPx.value)
    ) match
      case Right(t) if t.bytes.value.nonEmpty => Some(t)
      case Right(_)                           => None
      case Left(why)                          =>
        scribe.info(s"media: no thumbnail for $name (${why.sentence}) — the card shows the picture itself")
        None

  // ── illustrations ─────────────────────────────────────────────────────────────────────────────

  /** Choose (and unless `dryRun`, link) a source's illustration. `Right(line)` when it has one now, `Left(reason)` when
    * none of its figures fits. A source that already has this very figure keeps it.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def illustrate(
      citekey: Citekey,
      dryRun: SourcePictures.DryRun
  )(using WritePermit): Either[SourcePictures.IllustrateText, MediaSourceLine] =
    val figs = assets.sourceFigures(citekey)
    if figs.isEmpty then Left(SourcePictures.IllustrateText("no figures"))
    else
      val weighed = FigurePick.withRefs(
        figs.map(SourcePictures.toFigure),
        FigurePick.ConvertedText(sources.markdown(citekey).value)
      )
      FigurePick.choose(weighed, rules) match
        case None       => Left(SourcePictures.IllustrateText("no figure fit to illustrate it"))
        case Some(pick) =>
          val row = figs.find(_.name.value == pick.name.value).get
          val uid = AssetService.AssetUid(row.uid.value)
          val line = MediaSourceLine(
            com.histmersion.api.library.Citekey(citekey.value),
            MediaSourceLine.Role(MediaRole.Illustration),
            MediaItem.Provider(MediaProvider.Source),
            MediaSourceLine.Detail(pick.name.value)
          )
          if !dryRun.dryRun then
            if !row.hasThumb.hasThumb then
              assets
                .file(uid)
                .flatMap((_, b) => thumbnail(b, row.name.value))
                .foreach(t =>
                  assets.setThumb(
                    uid,
                    ProviderAsset.Thumb(t.bytes.value),
                    ProviderAsset.ThumbMime(t.mime.value),
                    Some(t.width.value).filter(_ > 0).map(ProviderAsset.ThumbWidth(_)),
                    Some(t.height.value).filter(_ > 0).map(ProviderAsset.ThumbHeight(_))
                  )
                )
            assets.setSourcePicture(citekey, uid, MediaRole.Wire(MediaRole.Illustration), None)
          Right(line)

  // ── covers ────────────────────────────────────────────────────────────────────────────────────

  /** A cover from the Open Library Covers API, by the source's ISBN (else OCLC number). */
  def openLibrary(
      keys: CoverKeys,
      dryRun: SourcePictures.DryRun,
      fetch: Polite
  )(using WritePermit)(using Creator.New): Either[CoverRefusal, MediaSourceLine] =
    fetch.synchronized(openLibraryNow(keys, dryRun, fetch))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def openLibraryNow(
      keys: CoverKeys,
      dryRun: SourcePictures.DryRun,
      fetch: Polite
  )(using WritePermit)(using Creator.New): Either[CoverRefusal, MediaSourceLine] =
    def refused(why: String) = Left(CoverRefusal.Skipped(SourcePictures.OpenLibraryText(why)))
    OpenLibrary.key(keys.isbn.map(i => Isbn(i.value)), keys.oclc.map(o => OpenLibrary.Oclc(o.value))) match
      case None             => refused("no usable ISBN or OCLC number")
      case Some((kind, id)) =>
        val line = MediaSourceLine(
          com.histmersion.api.library.Citekey(keys.citekey.value),
          MediaSourceLine.Role(MediaRole.Cover),
          MediaItem.Provider(MediaProvider.OpenLibrary),
          MediaSourceLine.Detail(s"${kind.value}:${id.value}")
        )
        if dryRun.dryRun then Right(line)
        else
          val c = cfg.covers
          val url = OpenLibrary.coverUrl(
            OpenLibrary.BaseUrl(c.openLibraryUrl.value),
            kind,
            id,
            OpenLibrary.CoverSize(c.size.value)
          )
          val got = fetch.file(HttpTransport.Url(url.value), Polite.Mime("image/jpeg")) match
            case Left(why) if why.answered.exists(_.isNotFound) => refused("Open Library has no cover")
            case Left(why)                                      =>
              Left(CoverRefusal.Failed(SourcePictures.OpenLibraryText(s"Open Library: ${why.sentence}")))
            case Right((bytes, _)) if bytes.value.length > cfg.maxBytes.value =>
              refused(s"cover of ${bytes.value.length} bytes is larger than media.max-bytes")
            case Right((bytes, mime)) if !mime.value.startsWith("image/") =>
              refused(s"Open Library answered $mime, not an image")
            case Right((bytes, mime)) =>
              val thumb = fetch
                .file(
                  HttpTransport.Url(
                    OpenLibrary
                      .coverUrl(
                        OpenLibrary.BaseUrl(c.openLibraryUrl.value),
                        kind,
                        id,
                        OpenLibrary.CoverSize(c.thumbSize.value)
                      )
                      .value
                  ),
                  mime
                )
                .toOption
                .filter((b, m) => b.value.nonEmpty && m.value.startsWith("image/"))
              val size = ImageSize.of(ImageSize.Bytes(bytes.value))
              val tsize = thumb.flatMap((b, _) => ImageSize.of(ImageSize.Bytes(b.value)))
              val a = ProviderAsset(
                ProviderAsset.Provider(MediaProvider.OpenLibrary),
                ProviderAsset.Key(s"${kind.value}:${id.value}"),
                ProviderAsset.Name(s"cover-${kind.value}-${id.value}.jpg"),
                ProviderAsset.Mime(mime.value),
                ProviderAsset.Bytes(bytes.value),
                size.map(s => ProviderAsset.Width(s._1.value)),
                size.map(s => ProviderAsset.Height(s._2.value)),
                thumb.map((b, _) => ProviderAsset.Thumb(b.value)),
                thumb.map((_, m) => ProviderAsset.ThumbMime(m.value)),
                tsize.map(s => ProviderAsset.ThumbWidth(s._1.value)),
                tsize.map(s => ProviderAsset.ThumbHeight(s._2.value)),
                license = ProviderAsset.License(""),
                licenseUrl = None,
                author = None,
                credit = ProviderAsset.Credit("Cover: Open Library"),
                sourceUrl = ProviderAsset.SourceUrl(
                  OpenLibrary.pageUrl(OpenLibrary.BaseUrl(c.openLibraryPage.value), kind, id).value
                ),
                originalUrl = Some(ProviderAsset.OriginalUrl(url.value.stripSuffix("?default=false"))),
                kind = Some(ProviderAsset.Kind(AssetKind.Photo))
              )
              val (uid, _) = assets.save(
                a,
                (AssetService.TargetType(PictureTarget.Source.wire.value), AssetService.TargetId(keys.citekey.value))
              )
              assets.setSourcePicture(
                keys.citekey,
                uid,
                MediaRole.Wire(MediaRole.Cover),
                Some(AssetService.LinkProperty(kind.value))
              )
              Right(line)
          imports.checked(
            ImportService.Provider(MediaProvider.OpenLibrary),
            ImportService.TargetType(PictureTarget.Source.wire.value),
            ImportService.TargetId(keys.citekey.value),
            if got.isRight then ImportService.FoundCount(1) else ImportService.FoundCount(0),
            None
          )
          got

  /** The polite client every Open Library call goes through — ONE per server, so its own, slower pace holds across runs
    * and the OCR jobs' covers alike (calls are serialised on it).
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  lazy val openLibraryClient: Polite =
    Polite(http, cfg.copy(requestIntervalMs = cfg.covers.requestIntervalMs))

  /** Pure: an image's type from its first bytes (`None` unless JPEG, PNG, GIF or WebP). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[covers] def sniff(b: AssetService.Bytes): Option[AssetService.Mime] =
    def at(i: Int) = if b.value.length > i then b.value(i) & 0xff else -1
    if at(0) == 0xff && at(1) == 0xd8 then Some(AssetService.Mime("image/jpeg"))
    else if at(0) == 0x89 && at(1) == 'P' && at(2) == 'N' && at(3) == 'G' then Some(AssetService.Mime("image/png"))
    else if at(0) == 'G' && at(1) == 'I' && at(2) == 'F' then Some(AssetService.Mime("image/gif"))
    else if b.value.length >= 12 && new String(b.value.slice(8, 12), "US-ASCII") == "WEBP" then
      Some(AssetService.Mime("image/webp"))
    else None

  /** Store `bytes` (read from the local Calibre library by the CLI) as the source's cover. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def storeCover(p: SourceCoverParams, bytes: AssetService.Bytes)(using
      WritePermit
  )(using Creator.New): Either[Api.Error, MediaItem] =
    val bad = (m: String) =>
      Left(Api.Error(Problem.Unclassified(Legacy.Text(m)), Some(Api.Error.Code(Write.CodeInvalid))))
    if p.provider.value != MediaProvider.Calibre then
      bad(s"a cover is uploaded from ${MediaProvider.Calibre} only (Open Library's come through `media covers`)")
    else if p.key.value.trim.isEmpty then bad("the cover's key (its Calibre id) is missing")
    else if bytes.value.isEmpty then bad("the cover is empty")
    else if bytes.value.length > cfg.maxBytes.value then
      bad(s"the cover is ${bytes.value.length} bytes, more than media.max-bytes")
    else
      sniff(bytes) match
        case None       => bad("the cover is not a JPEG, PNG, GIF or WebP image")
        case Some(mime) =>
          Citekey.option(p.citekey.value.trim).flatMap(sources.current) match
            case None =>
              Left(
                Api.Error(
                  Problem.NotFound.Entity(Problem.TypeName.of(Citekey), Problem.Raw(s"${p.citekey}")),
                  Some(Api.Error.Code(Write.CodeNotFound))
                )
              )
            case Some(current) =>
              val ck = current.citekey
              val key = p.key.value.trim
              val size = ImageSize.of(ImageSize.Bytes(bytes.value))
              val thumb = thumbnail(bytes, s"cover-$key")
              val a = ProviderAsset(
                ProviderAsset.Provider(MediaProvider.Calibre),
                ProviderAsset.Key(key),
                ProviderAsset.Name(
                  MediaText.safeName(WorkKey.Title(s"cover-calibre-$key.${mime.value.drop(6)}")).value
                ),
                ProviderAsset.Mime(mime.value),
                ProviderAsset.Bytes(bytes.value),
                size.map(s => ProviderAsset.Width(s._1.value)),
                size.map(s => ProviderAsset.Height(s._2.value)),
                thumb.map(t => ProviderAsset.Thumb(t.bytes.value)),
                thumb.map(t => ProviderAsset.ThumbMime(t.mime.value)),
                thumb.map(_.width.value).filter(_ > 0).map(ProviderAsset.ThumbWidth(_)),
                thumb.map(_.height.value).filter(_ > 0).map(ProviderAsset.ThumbHeight(_)),
                license = ProviderAsset.License(""),
                licenseUrl = None,
                author = None,
                credit = ProviderAsset.Credit("Cover: the local Calibre library"),
                sourceUrl = ProviderAsset.SourceUrl(s"calibre:book/$key"),
                originalUrl = None,
                kind = Some(ProviderAsset.Kind(AssetKind.Photo))
              )
              val (uid, _) = assets.save(
                a,
                (AssetService.TargetType(PictureTarget.Source.wire.value), AssetService.TargetId(ck.value))
              )
              assets.setSourcePicture(
                ck,
                uid,
                MediaRole.Wire(MediaRole.Cover),
                Some(AssetService.LinkProperty(MediaProvider.Calibre))
              )
              assets.show(uid).toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"cover of '$ck' not stored"))))

  // ── a run over the corpus ─────────────────────────────────────────────────────────────────────

  /** Covers (Open Library) and illustrations for the sources a request names; `progress` hears how far it got. A dry
    * run reads the database only.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def run(req: MediaCoversRequest, progress: JobInfo.Progress => Unit = _ => ())(using
      WritePermit
  )(using Creator.New): MediaCoversReport =
    val dryRun = (if req.dryRun.dryRun then SourcePictures.DryRun.DryRun else SourcePictures.DryRun.Applied)
    val refresh =
      (if req.refresh.refresh then CoverService.CandidatesRefresh.Refresh else CoverService.CandidatesRefresh.Reuse)
    val named = req.citekey.map(_.value.trim).filter(_.nonEmpty)
    val only = named.flatMap(c => Citekey.option(c).map(k => sources.current(k).map(cur => cur.citekey).getOrElse(k)))
    // a citekey that cannot be one names no source
    val nobody = named.isDefined && only.isEmpty
    val limit = CoverService.CandidatesLimit(req.limit.map(_.value).filter(_ > 0).getOrElse(Int.MaxValue))
    val skipped = List.newBuilder[String]
    val failed = List.newBuilder[String]
    val lines = List.newBuilder[MediaSourceLine]
    // `var`: the sources looked at so far
    var seen = Set.empty[String]

    if req.covers.included then
      val hinted = for ck <- only; i <- req.isbn.map(_.value.trim).filter(_.nonEmpty)
      yield CoverKeys(ck, Some(CoverKeys.Isbn(i)), None)
      val cands =
        if nobody then Nil
        else
          covers.coverCandidates(only, refresh, limit) match
            case Nil =>
              hinted
                .filter(k =>
                  refresh.candidatesRefresh || assets.sourcePicture(k.citekey, MediaRole.Wire(MediaRole.Cover)).isEmpty
                )
                .toList
            case cs => cs
      val fetch = openLibraryClient
      cands.zipWithIndex.foreach { (k, i) =>
        seen += k.citekey.value
        progress(JobInfo.Progress(s"Open Library covers: ${i + 1}/${cands.size} (${k.citekey})"))
        openLibrary(k, dryRun, fetch) match
          case Right(l)                        => lines += l
          case Left(CoverRefusal.Failed(why))  => failed += s"${k.citekey}: $why"
          case Left(CoverRefusal.Skipped(why)) => skipped += s"cover: $why"
      }

    if req.illustrations.included then
      val cands = if nobody then Nil else covers.illustrationCandidates(only, refresh, limit)
      cands.zipWithIndex.foreach { (ck, i) =>
        seen += ck.value
        if i % 10 == 0 then progress(JobInfo.Progress(s"illustrations: ${i + 1}/${cands.size}"))
        try
          illustrate(ck, dryRun) match
            case Right(l)  => lines += l
            case Left(why) => skipped += s"illustration: ${why.value}"
        catch
          case e: InterruptedException => throw e
          case e: Exception            => failed += s"$ck: ${e.getClass.getSimpleName}: ${e.getMessage}"
      }

    val ls = lines.result()
    MediaCoversReport(
      req.dryRun,
      MediaCoversReport.Sources(seen.size),
      MediaCoversReport.Covers(ls.count(_.role.value == MediaRole.Cover)),
      MediaCoversReport.Illustrations(ls.count(_.role.value == MediaRole.Illustration)),
      ls,
      MediaCount.of(skipped.result().map(MediaCount.Key(_))),
      failed.result().map(MediaCoversReport.Failed(_))
    )

  /** After a conversion stored a source's figures: its illustration, and — when its record holds an ISBN or OCLC number
    * and it has no cover yet — Open Library's cover. Best-effort: a note for the job's progress line, never a failure.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def afterIngest(citekey: Citekey)(using WritePermit)(using Creator.New): Option[SourcePictures.AfterIngestText] =
    try
      val ill =
        illustrate(citekey, dryRun = SourcePictures.DryRun.Applied).toOption.map(l => s"illustration ${l.detail}")
      val cover = covers
        .coverCandidates(Some(citekey), CoverService.CandidatesRefresh.Reuse, CoverService.CandidatesLimit(1))
        .headOption
        .flatMap(k => openLibrary(k, SourcePictures.DryRun.Applied, openLibraryClient).toOption)
        .map(l => s"cover ${l.detail}")
      Option(List(ill, cover).flatten).filter(_.nonEmpty).map(l => SourcePictures.AfterIngestText(l.mkString(" · ")))
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        scribe.warn(s"media: no illustration for $citekey: ${e.getMessage}")
        None

object SourcePictures:
  type AfterIngestText = AfterIngestText.T
  object AfterIngestText extends Newtype[String, Pure]
  enum DryRun derives CanEqual:
    case DryRun, Applied
  object DryRun extends TwoCases[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
  type IllustrateText = IllustrateText.T
  object IllustrateText extends Newtype[String, Pure]
  type OpenLibraryText = OpenLibraryText.T
  object OpenLibraryText extends Newtype[String, Pure]

  /** Why no Open Library cover was kept: nothing to ask or to keep (`Skipped`), or Open Library could not be asked
    * (`Failed`, reported as an error).
    */
  enum CoverRefusal:
    case Skipped(why: OpenLibraryText)
    case Failed(why: OpenLibraryText)

  /** Pure: a figure row as [[FigurePick]] weighs it, before the text's links to it are counted. */

  def toFigure(f: SourceFigure): FigurePick.Figure =
    f.into[FigurePick.Figure].enableDefaultValues.transform
