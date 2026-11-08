package com.histmersion
package util.data.diagnostics

import com.histmersion.util.storage.Location
import io.github.iltotore.iron.constraint.any.Pure

/** How About › Storage shows a store: its entry's key, label and group. About lists them in this order. */
enum ShownStore(val location: Location, val key: ShownStore.Key, val label: ShownStore.Label, val group: ShownStore.Group):
  case DatabaseCluster
      extends ShownStore(
        Location.DatabaseCluster,
        ShownStore.Key("database-cluster"),
        ShownStore.Label("database files (bundled Postgres)"),
        ShownStore.Group("database")
      )
  case Backups
      extends ShownStore(
        Location.Backups,
        ShownStore.Key("backups"),
        ShownStore.Label("backups"),
        ShownStore.Group("backups")
      )
  case UploadedOriginals
      extends ShownStore(
        Location.IngestOriginals,
        ShownStore.Key("originals"),
        ShownStore.Label("stored originals (uploads)"),
        ShownStore.Group("corpus")
      )
  case AcquireSpool
      extends ShownStore(
        Location.Spool,
        ShownStore.Key("acquire-spool"),
        ShownStore.Label("acquisition scratch"),
        ShownStore.Group("corpus")
      )
  case Corpus
      extends ShownStore(
        Location.CorpusFolder,
        ShownStore.Key("corpus"),
        ShownStore.Label("corpus folder (seed)"),
        ShownStore.Group("corpus")
      )
  case Bundles
      extends ShownStore(
        Location.Bundles,
        ShownStore.Key("bundles"),
        ShownStore.Label("bundle scratch"),
        ShownStore.Group("corpus")
      )
  case GazetteerCache
      extends ShownStore(
        Location.GazetteerExtracts,
        ShownStore.Key("gazetteer"),
        ShownStore.Label("gazetteer extracts (cache)"),
        ShownStore.Group("caches")
      )
  case GazetteerData
      extends ShownStore(
        Location.GazetteerFolder,
        ShownStore.Key("gazetteer-data"),
        ShownStore.Label("gazetteer folder (data)"),
        ShownStore.Group("caches")
      )
  case GazetteerLpf
      extends ShownStore(
        Location.GazetteerDrop,
        ShownStore.Key("gazetteer-lpf"),
        ShownStore.Label("gazetteer LPF drop folder"),
        ShownStore.Group("caches")
      )
  case Basemap
      extends ShownStore(
        Location.Basemap,
        ShownStore.Key("basemap"),
        ShownStore.Label("terrain base map (cache)"),
        ShownStore.Group("caches")
      )
  case Install
      extends ShownStore(
        Location.Install,
        ShownStore.Key("install"),
        ShownStore.Label("install (binaries, Postgres, converter)"),
        ShownStore.Group("install")
      )
  case Converter
      extends ShownStore(
        Location.Converter,
        ShownStore.Key("converter"),
        ShownStore.Label("OCR converter (Python, models)"),
        ShownStore.Group("install")
      )
  case RelinkKit
      extends ShownStore(
        Location.RelinkKit,
        ShownStore.Key("relink-kit"),
        ShownStore.Label("plugin relink kit"),
        ShownStore.Group("install")
      )
  case Toolchain
      extends ShownStore(
        Location.Toolchain,
        ShownStore.Key("toolchain"),
        ShownStore.Label("GraalVM toolchain"),
        ShownStore.Group("install")
      )
  case Plugins
      extends ShownStore(
        Location.Plugins,
        ShownStore.Key("plugins"),
        ShownStore.Label("plugins"),
        ShownStore.Group("install")
      )
  case AgentRuntimes
      extends ShownStore(
        Location.AgentHomes,
        ShownStore.Key("agents"),
        ShownStore.Label("agent definitions"),
        ShownStore.Group("install")
      )
  case CodexSessions
      extends ShownStore(
        Location.CodexSessions,
        ShownStore.Key("codex-home"),
        ShownStore.Label("Codex home of the agent runs (transcripts, state)"),
        ShownStore.Group("other")
      )
  case Logs
      extends ShownStore(
        Location.Logs,
        ShownStore.Key("logs"),
        ShownStore.Label("logs"),
        ShownStore.Group("logs")
      )

object ShownStore:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Group = Group.T
  object Group extends Newtype[String, Pure]
