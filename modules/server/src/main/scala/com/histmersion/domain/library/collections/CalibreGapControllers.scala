package com.histmersion.domain.library.collections

import com.histmersion.api.Write
import com.histmersion.config.AppConfig
import com.histmersion.domain.Caller
import com.histmersion.domain.library.ingest.StoredOriginals
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.library.Citekey

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.{CalibreGap, CalibreGaps, CalibrePushRequest, CalibrePushResult}
import com.histmersion.api.Api

/** The sources not in Calibre yet, what a push names one by, and the Calibre id written back after a push — what the
  * Calibre collection and the CLI's host-side push read and write.
  */
final class CalibreGapControllers(
    sources: SourceService,
    originals: StoredOriginals,
    cfg: () => AppConfig,
    maintenance: WriteGate
):

  def gaps(limit: Option[com.histmersion.domain.library.collections.CalibreGapControllers.Limit]): CalibreGaps =
    // capped like every other listing: `calibreGaps(None)` used to mean LIMIT Int.MaxValue
    val limits = cfg().limits
    val gaps = sources.calibreGaps(Some(limits.page(limit, limits.maxPage)))
    // which originals the server can hand the host's push (`GET /api/sources/{ck}/original`)
    val hashes = sources.originals(gaps.map(_.citekey))
    val stored = if hashes.isEmpty then Set.empty[String] else originals.hashes
    val marked = gaps.map(g => g.modify(_.storedOriginal).setTo(Some(hashes.get(g.citekey).exists(stored))))
    CalibreGaps(marked.size, marked)

  def pushMeta(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, CalibreGap] =
    Citekey
      .parse(citekey)
      .left
      .map(Write.invalid)
      .flatMap(_ => sources.calibrePushMeta(citekey).toRight(Write.notFound(citekey)))

  def pushed(req: CalibrePushRequest): Either[Api.Error, CalibrePushResult] =
    maintenance.guard("calibre/pushed"):
      Citekey.parse(req.citekey).left.map(Api.Error(_)).flatMap(_ => sources.setCalibreId(req.citekey, req.calibreId, Caller.Local).map(_ => CalibrePushResult(req.citekey, req.calibreId, ok = true)))

object CalibreGapControllers:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
