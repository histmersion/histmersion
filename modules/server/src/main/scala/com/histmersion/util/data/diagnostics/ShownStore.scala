package com.histmersion.util.data.diagnostics

import com.histmersion.util.storage.Location

/** How About › Storage shows a store: its entry's key, label and group. About lists them in this order. */
enum ShownStore(val location: Location, val key: String, val label: String, val group: String):
  case DatabaseCluster
      extends ShownStore(Location.DatabaseCluster, "database-cluster", "database files (bundled Postgres)", "database")
  case Backups extends ShownStore(Location.Backups, "backups", "backups", "backups")
  case UploadedOriginals extends ShownStore(Location.IngestOriginals, "originals", "stored originals (uploads)", "corpus")
  case AcquireSpool extends ShownStore(Location.Spool, "acquire-spool", "acquisition scratch", "corpus")
  case Corpus extends ShownStore(Location.CorpusFolder, "corpus", "corpus folder (seed)", "corpus")
  case Bundles extends ShownStore(Location.Bundles, "bundles", "bundle scratch", "corpus")
  case GazetteerCache extends ShownStore(Location.GazetteerExtracts, "gazetteer", "gazetteer extracts (cache)", "caches")
  case GazetteerData extends ShownStore(Location.GazetteerFolder, "gazetteer-data", "gazetteer folder (data)", "caches")
  case GazetteerLpf extends ShownStore(Location.GazetteerDrop, "gazetteer-lpf", "gazetteer LPF drop folder", "caches")
  case Basemap extends ShownStore(Location.Basemap, "basemap", "terrain base map (cache)", "caches")
  case Install extends ShownStore(Location.Install, "install", "install (binaries, Postgres, converter)", "install")
  case Converter extends ShownStore(Location.Converter, "converter", "OCR converter (Python, models)", "install")
  case RelinkKit extends ShownStore(Location.RelinkKit, "relink-kit", "plugin relink kit", "install")
  case Toolchain extends ShownStore(Location.Toolchain, "toolchain", "GraalVM toolchain", "install")
  case Plugins extends ShownStore(Location.Plugins, "plugins", "plugins", "install")
  case AgentRuntimes extends ShownStore(Location.AgentHomes, "agents", "agent definitions", "install")
  case CodexSessions
      extends ShownStore(Location.CodexSessions, "codex-home", "Codex home of the agent runs (transcripts, state)", "other")
  case Logs extends ShownStore(Location.Logs, "logs", "logs", "logs")
