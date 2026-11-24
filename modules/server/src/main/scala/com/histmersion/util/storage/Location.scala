package com.histmersion
package util.storage

import com.histmersion.lint.{LintRule, tolerated}
/** The server's named places on disk. Configuration decides where each lives; the composition root works the
  * directories out from today's settings, so no install changes. The pictures, their thumbnails and the figures are
  * rows of the database, not files, and have no location.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
enum Location derives CanEqual:
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

  /** The bundle's install: its binaries, PostgreSQL and the converter (`runtime.home`). */
  case Install

  /** The bundle's logs (`<data dir>/logs`). */
  case Logs

  /** The directory of the user's settings file. */
  case UserConfig

  /** A temporary directory one operation makes and removes ([[Storage.scratch]]). */
  case Scratch

  /** The bundled PostgreSQL's cluster (`<data dir>/pg`). */
  case DatabaseCluster

  /** The folder the seed importer reads (`corpus.dir`). */
  case CorpusFolder

  /** The gazetteer folder older installs kept in the data directory. */
  case GazetteerFolder

  /** The folder Linked Places files are dropped into for a gazetteer load (`places.sources.lpf-dir`). */
  case GazetteerDrop

  /** The bundled gazetteer seed the init job imports (`<init.data-dir>/places`, the repo's `db/places`). */
  case GazetteerSeed

  /** The bundle's OCR converter: its Python and its models (`<install>/converter`). */
  case Converter

  /** The GraalVM toolchain a relink of the plugins uses (`<data dir>/toolchain`). */
  case Toolchain

  /** The Codex runtime's home of the agent runs: its transcripts and state. */
  case CodexSessions

  /** The data directory itself (`runtime.data-dir`), for what no other location covers. */
  case DataDirectory

  /** The per-user cache directory, for what no other location covers. */
  case CacheDirectory

  /** The bundle's PostgreSQL (`<install>/pg`), which a moved bundle rewrites to its new place. */
  case Postgres

  /** The directory of the PostgreSQL client tools a backup runs (`backups.pg-bin-dir`). */
  case PostgresTools

  /** One directory of the executables' search path (`PATH`). */
  case Executables
