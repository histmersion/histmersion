package com.histmersion
package domain.library.collections

import com.histmersion.api.Write
import com.histmersion.config.AppConfig
import com.histmersion.domain.Caller
import com.histmersion.domain.library.ingest.StoredOriginals
import com.histmersion.PageSize
import com.histmersion.domain.library.sources.{FullSourceRow, SourceService}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.library.Citekey

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.{CalibreGap, CalibreGaps, CalibrePushRequest, CalibrePushResult}
import com.histmersion.api.Api
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The sources not in Calibre yet, what a push names one by, and the Calibre id written back after a push — what the
  * Calibre collection and the CLI's host-side push read and write.
  */
final class CalibreGapControllers(
    sources: SourceService,
    originals: StoredOriginals,
    cfg: () => AppConfig,
    maintenance: WriteGate
):

  def gaps(limit: Option[CalibreGapControllers.Limit]): CalibreGaps =
    // capped like every other listing: `calibreGaps(None)` used to mean LIMIT Int.MaxValue
    val limits = cfg().limits
    val page = limits.page(
      limit.flatMap(l => PageSize.option(l.value)),
      PageSize.assume(math.max(1, limits.maxPage.value))
    )
    val gaps = sources.calibreGaps(Some(page))
    // which originals the server can hand the host's push (`GET /api/sources/{ck}/original`)
    def held(ck: com.histmersion.api.library.Citekey): Citekey = Citekey.assume(ck.value)
    val hashes = sources.originals(gaps.map(g => held(g.citekey)))
    val stored = if hashes.isEmpty then Set.empty[Sha256] else originals.hashes
    val marked = gaps.map(g => g.modify(_.storedOriginal).setTo(Some(CalibreGap.StoredOriginal(hashes.get(held(g.citekey)).exists(stored)))))
    CalibreGaps(CalibreGaps.Count(marked.size), marked)

  def pushMeta(citekey: Citekey): Either[Api.Error, CalibreGap] =
    sources.calibrePushMeta(citekey).toRight(Write.notFound(com.histmersion.api.library.Citekey(citekey.value)))

  def pushed(req: CalibrePushRequest): Either[Api.Error, CalibrePushResult] =
    maintenance.guard(WriteGate.OperationName("calibre/pushed")):
      Citekey
        .parse(req.citekey.value)
        .left
        .map(p => Api.Error(Api.Error.Message(p.value)))
        .flatMap(ck =>
          sources
            .setCalibreId(ck, FullSourceRow.CalibreId(req.calibreId.value), Caller.Local)
            .map(_ => CalibrePushResult(req.citekey, req.calibreId, ok = CalibrePushResult.Ok(true)))
        )

object CalibreGapControllers:
  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]
