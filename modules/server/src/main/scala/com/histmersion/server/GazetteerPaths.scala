package com.histmersion.server

import com.histmersion.config.{AppConfig, UserPaths}

import java.nio.file.{Files, Path}

/** Where the gazetteer's files are, resolved from the config: the pinned extracts (a CACHE, rebuilt or copied, pinned
  * by sha256: the XDG cache directory), the Linked Places drop folder, the manifest of the pins, and `legacy`, where
  * the extracts lived before they moved to the cache directory (`<data dir>/gazetteer`, until 2026-10-17).
  */
final case class GazetteerPaths(extracts: Path, lpf: Path, manifest: Path, legacy: Option[Path] = None):

  /** The manifest's text, or why there is none. */
  def manifestText(): Either[String, String] =
    if !Files.isRegularFile(manifest) then Left(s"no gazetteer manifest at $manifest (set places.sources.manifest)")
    else Right(Files.readString(manifest))

object GazetteerPaths:
  def of(cfg: AppConfig, env: String => Option[String] = sys.env.get): GazetteerPaths =
    val data = UserPaths
      .dataDir(cfg.runtime.dataDir, env, System.getProperty("os.name", ""))
      .getOrElse(Path.of(cfg.init.dataDir))
      .resolve("gazetteer")
    val cache = UserPaths.cacheDir(env).map(_.resolve("gazetteer")).getOrElse(data)
    def or(configured: String, fallback: Path): Path =
      Option(configured)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(p => Path.of(p.replaceFirst("^~", System.getProperty("user.home"))))
        .getOrElse(fallback)
    val extracts = or(cfg.places.sources.dir, cache)
    GazetteerPaths(
      extracts,
      or(cfg.places.sources.lpfDir, data.resolve("lpf")),
      Path.of(cfg.init.dataDir).resolve(cfg.places.sources.manifest),
      Option.when(data != extracts)(data)
    )
