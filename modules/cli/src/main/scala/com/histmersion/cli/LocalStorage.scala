package com.histmersion.cli

import com.histmersion.core.*

import java.nio.file.Path

/** The Storage part of `histmersion about` that only the CLI can see: the locations on the machine
  * the CLI runs on that the server knows nothing of — the host directory of Calibre originals
  * (`cli.calibre.originals-dir`) and the install the plugin commands manage (`cli.plugins.install-dir`).
  * Added only when the server is on this same machine (a loopback URL): otherwise "free space" and
  * "the install" would describe two different computers in one list. */
object LocalStorage:

  /** The locations, from the CLI's config. Pure but for finding the install (it must hold a kit). */
  def locations(cfg: AppConfig, env: String => Option[String], osName: String, exe: Option[Path]): List[(String, String, Path)] =
    cfg.cli.calibre.originals.map(p => ("calibre-originals", "originals folder (cli.calibre.originals-dir)", Path.of(p))).toList ++
      LocalInstall.locate(cfg.cli.plugins, cfg.runtime.dataDir, env, osName, exe).toOption
        .map(i => ("install", "install (binaries, Postgres, converter)", i.home)).toList

  /** Measure them within the `storage.*` caps and add them to `about`'s report. */
  def add(a: About, serverUrl: String, cfg: AppConfig, env: String => Option[String], osName: String,
      exe: Option[Path]): About =
    if !PluginKit.isLoopback(serverUrl) then a
    else
      val budget = DirSize.Budget.of(cfg.storage.maxEntries.toLong, cfg.storage.maxMillis.toLong)
      val locs = locations(cfg, env, osName, exe).map((k, l, p) => (k, l, p.toAbsolutePath.normalize))
      val entries = locs.map { (key, label, p) =>
        val r = DirSize.measure(p, budget)
        StorageEntry(key, label, if key == "install" then "install" else "corpus", Some(p.toString),
          bytes = Option.when(r.exists)(r.bytes), files = Option.when(r.exists)(r.files), exists = r.exists,
          partial = r.partial, volume = DirSize.volume(p).map(_.id), host = "cli")
      }
      val volumes = locs.flatMap((_, _, p) => DirSize.volume(p)).distinctBy(_.id)
      a.copy(storage = Storage.addLocal(a.storage, entries, volumes))
