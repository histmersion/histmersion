package com.histmersion
package server

import com.histmersion.domain.library.ingest.{IngestControllers, IngestQueue, QueuedIngest, SeedImport, Upload}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.domain.media.assets.AssetService
import com.histmersion.util.jobs.JobRecords
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.domain.library.acquisition.Spool
import com.histmersion.util.storage.{Location, Store}
import com.histmersion.domain.library.Citekey

/** The library's ingest over one server's `deps`: the job queue over the converter and the originals' store, the ingest
  * surface, the hand-over an acquisition's fetched copy takes, and the seed. The figures are media's.
  */
final class IngestWiring(
    deps: Deps,
    converter: ConverterClient,
    sources: SourceService,
    figures: AssetService,
    afterStore: Citekey => Option[IngestQueue.Note]
):
  private def originals: Store = deps.storage.store(Location.IngestOriginals)

  /** The acquisition spool, where a fetched copy waits for the converter. */
  def spool: Spool = Spool(deps.storage.store(Location.Spool))

  val queue: IngestQueue = IngestQueue(
    sources,
    figures,
    deps.queues,
    JobRecords(() => deps.ds),
    () => originals,
    converter,
    () => deps.cfg,
    deps.maintenance,
    deps.clock,
    () => deps.ds,
    deps.pins,
    afterStore
  )

  val controllers: IngestControllers =
    IngestControllers(
      sources,
      deps.queues,
      queue,
      () => originals,
      ServerToolFiles(() => deps.cfg),
      () => deps.cfg,
      deps.maintenance
    )

  /** What an acquisition hands its fetched copy to: the metadata at once, the file to the OCR queue. */
  val jobs: QueuedIngest = QueuedIngest(controllers, sources, c => spool.get(c.key).map(Upload.stored))

  val seed: SeedImport = SeedImport(sources, figures.addFigures)

