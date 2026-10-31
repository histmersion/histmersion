package com.histmersion.domain.library.sources

import com.histmersion.api.ReindexResult
import com.histmersion.db.SourceRepo
import com.histmersion.util.data.DataOwner
import com.histmersion.util.data.TablesAdapter.LibraryOwner
import com.histmersion.util.data.maintenance.{MaintenanceSite, MaintenanceSlice, MaintenanceTask, MaintenanceTaskName}

/** Today's upkeep of the sources, registered from here (it runs their repository) until the library registers its own
  * contribution: the rebuild of the full-text chunks, in resumable slices.
  */
object SourcesMaintenanceAdapter:

  /** The rebuild of every source's full-text chunks from its OCR'd body: at most `limit` sources after `after` (and at
    * most `limits.reindex-chars` characters) per slice.
    */
  val reindex: MaintenanceTask = new MaintenanceTask:
    val owner: DataOwner = LibraryOwner
    val name: MaintenanceTaskName = MaintenanceTaskName("reindex")
    def run(slice: MaintenanceSlice, site: MaintenanceSite): ReindexResult =
      SourceRepo.reindexChunks(
        site.dataSource,
        site.settings.limits.chunkChars,
        slice.after,
        slice.limit,
        site.settings.limits.reindexChars
      )

  val tasks: List[MaintenanceTask] = List(reindex)
