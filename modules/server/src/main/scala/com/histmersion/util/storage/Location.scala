package com.histmersion.util.storage

/** The server's named places on disk. Configuration decides where each lives; the composition root works the
  * directories out from today's settings, so no install changes. The pictures, their thumbnails and the figures are
  * rows of the database, not files, and have no location.
  */
enum Location:
  /** The originals stored at ingest, content-addressed (`converter.spool-dir`). */
  case IngestOriginals

  /** Fetched files waiting for the converter (`acquire.spool-dir`). */
  case Spool

  /** The base map's extracts and their caches (`basemap.dir`). */
  case Basemap

  /** The database dumps (`backups.dir`). */
  case Backups

  /** Bundles being built or read (`bundles.dir`). */
  case Bundles

  /** The pinned gazetteer extracts (`places.gazetteer.dir`). */
  case GazetteerExtracts

  /** The agent runtimes' generated trees and homes (`agents.runtimes.dir`). */
  case AgentHomes

  /** The plugins a relink links in (`<data dir>/plugins`). */
  case Plugins

  /** The install's relink kit (`<install>/kit`). */
  case RelinkKit

  /** The directory of the user's settings file. */
  case UserConfig

  /** A temporary directory one operation makes and removes ([[Storage.scratch]]). */
  case Scratch
