package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*

import java.nio.file.Path
import com.histmersion.util.plugins.PluginKit
import com.histmersion.api.instance.About
import com.histmersion.api.maintenance.Storage

/** The Storage part of `histmersion about` that only the CLI can see: the locations on the machine the CLI runs on that
  * the server knows nothing of — the host directory of Calibre originals (`cli.calibre.originals-dir`) and the install
  * the plugin commands manage (`cli.plugins.install-dir`). Added only when the server is on this same machine (a
  * loopback URL): otherwise "free space" and "the install" would describe two different computers in one list.
  */
object LocalStorage:
  type Path = Path.T
  object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The locations, from the CLI's config. Pure but for finding the install (it must hold a kit). */
  type AddOsName = _root_.com.histmersion.cli.LocalInstall.OsName
  val AddOsName: _root_.com.histmersion.cli.LocalInstall.OsName.type = _root_.com.histmersion.cli.LocalInstall.OsName

  def locations(
      cfg: AppConfig,
      env: com.histmersion.cli.LocalInstall.EnvVariable => Option[com.histmersion.cli.LocalInstall.EnvSetting],
      osName: com.histmersion.cli.LocalInstall.OsName,
      exe: Option[Path]
  ): List[(com.histmersion.cli.LocalStorage.Path, com.histmersion.cli.LocalStorage.Path, Path)] =
    cfg.cli.calibre.originals
      .map(p => ("calibre-originals", "originals folder (cli.calibre.originals-dir)", Path.of(p)))
      .toList ++
      LocalInstall
        .locate(cfg.cli.plugins, cfg.runtime.dataDir, env, osName, exe)
        .toOption
        .map(i => ("install", "install (binaries, Postgres, converter)", i.home))
        .toList

  /** Measure them within the `storage.*` caps and add them to `about`'s report. */
  def add(
      a: About,
      serverUrl: com.histmersion.cli.Client.ServerUrl,
      cfg: AppConfig,
      env: com.histmersion.cli.LocalInstall.EnvVariable => Option[com.histmersion.cli.LocalInstall.EnvSetting],
      osName: com.histmersion.cli.LocalStorage.AddOsName,
      exe: Option[Path]
  ): About =
    if !PluginKit.isLoopback(serverUrl) then a
    else
      val budget = DirSize.Budget.of(cfg.storage.maxEntries.toLong, cfg.storage.maxMillis.toLong)
      val locs = locations(cfg, env, osName, exe).map((k, l, p) => (k, l, p.toAbsolutePath.normalize))
      val entries = locs.map { (key, label, p) =>
        val r = DirSize.measure(p, budget)
        Storage.Entry(
          key,
          label,
          if key == "install" then com.histmersion.api.maintenance.Storage.Entry.Group("install") else com.histmersion.api.maintenance.Storage.Entry.Group("corpus"),
          Some(p.toString),
          bytes = (Option.when(r.exists)(r.bytes)).map(com.histmersion.api.ByteCount.apply),
          files = (Option.when(r.exists)(r.files)).map(com.histmersion.api.maintenance.Storage.FileCount.apply),
          exists = r.exists,
          partial = r.partial,
          volume = DirSize.volume(p).map(_.id),
          host = "cli"
        )
      }
      val volumes = locs.flatMap((_, _, p) => DirSize.volume(p)).distinctBy(_.id)
      a.modify(_.storage).setTo(Storage.addLocal(a.storage, entries, volumes))
