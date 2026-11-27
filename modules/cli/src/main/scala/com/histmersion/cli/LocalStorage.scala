package com.histmersion
package cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*

import java.nio.file.Path
import com.histmersion.util.plugins.PluginKit
import com.histmersion.api.instance.About
import com.histmersion.api.maintenance.Storage
import com.histmersion.lint.{tolerated, LintRule}

/** The Storage part of `histmersion about` that only the CLI can see: the locations on the machine the CLI runs on that
  * the server knows nothing of — the host directory of Calibre originals (`cli.calibre.originals-dir`) and the install
  * the plugin commands manage (`cli.plugins.install-dir`). Added only when the server is on this same machine (a
  * loopback URL): otherwise "free space" and "the install" would describe two different computers in one list.
  */
object LocalStorage:
  /** The locations, from the CLI's config. Pure but for finding the install (it must hold a kit). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def locations(
      cfg: AppConfig,
      env: UserPaths.Lookup,
      osName: UserPaths.OsName,
      exe: Option[Path]
  ): List[(Storage.Entry.Key, Storage.Entry.Label, Path)] =
    cfg.cli.calibre.originals
      .map(p =>
        (
          Storage.Entry.Key("calibre-originals"),
          Storage.Entry.Label("originals folder (cli.calibre.originals-dir)"),
          Path.of(p.value)
        )
      )
      .toList ++
      LocalInstall
        .locate(cfg.cli.plugins, cfg.runtime.dataDir, env, osName, exe)
        .toOption
        .map(i =>
          (Storage.Entry.Key("install"), Storage.Entry.Label("install (binaries, Postgres, converter)"), i.home)
        )
        .toList

  /** Measure them within the `storage.*` caps and add them to `about`'s report. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def add(
      a: About,
      serverUrl: Client.ServerUrl,
      cfg: AppConfig,
      env: UserPaths.Lookup,
      osName: UserPaths.OsName,
      exe: Option[Path]
  ): About =
    if !PluginKit.isLoopback(serverUrl.value) then a
    else
      val budget = DirSize.Budget.of(cfg.storage.maxEntries, cfg.storage.maxMillis)
      val locs = locations(cfg, env, osName, exe).map((k, l, p) => (k, l, p.toAbsolutePath.normalize))
      val entries = locs.map { (key, label, p) =>
        val r = DirSize.measure(p, budget)
        Storage.Entry(
          key,
          label,
          if key.value == "install" then Storage.Entry.Group("install") else Storage.Entry.Group("corpus"),
          Some(Storage.Entry.Path(p.toString)),
          bytes = Option.when(r.exists.value)(r.bytes),
          files = Option.when(r.exists.value)(r.files),
          exists = r.exists,
          partial = r.partial,
          volume = DirSize.volume(p).map(v => Storage.Entry.Volume(v.id.value)),
          host = Storage.Entry.Host("cli")
        )
      }
      val volumes = locs.flatMap((_, _, p) => DirSize.volume(p)).distinctBy(_.id)
      a.modify(_.storage).setTo(Storage.addLocal(a.storage, entries, volumes))
