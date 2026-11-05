package com.histmersion.server

import com.histmersion.config.{AppConfig, UserPaths}

import java.nio.file.{Files, Path}

/** Where the gazetteer's files are, resolved from the config: the pinned extracts (a CACHE, rebuilt or copied, pinned
  * by sha256: the XDG cache directory), the Linked Places drop folder, the manifest of the pins, and `legacy`, where
  * the extracts lived before they moved to the cache directory (`<data dir>/gazetteer`, until 2026-10-17).
  */
final case class GazetteerPaths(extracts: Path, lpf: Path, manifest: Path, legacy: Option[Path] = None):

  /** The manifest's text, or why there is none. */
  def manifestText(): Either[com.histmersion.server.GazetteerPaths.ManifestTextResultPart1, com.histmersion.server.GazetteerPaths.ManifestTextResultPart2] =
    if !Files.isRegularFile(manifest) then Left(s"no gazetteer manifest at $manifest (set places.sources.manifest)")
    else Right(Files.readString(manifest))

object GazetteerPaths:
  /** The paths in the process environment `env` (the entry point read it once) of the operating system `osName`. */
  type OfOrConfigured = OfOrConfigured.T
  object OfOrConfigured extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ManifestTextResultPart1 = ManifestTextResultPart1.T
  object ManifestTextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ManifestTextResultPart2 = ManifestTextResultPart2.T
  object ManifestTextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfEnvArg1 = OfEnvArg1.T
  object OfEnvArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfEnvResult = OfEnvResult.T
  object OfEnvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfOsName = OfOsName.T
  object OfOsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(cfg: AppConfig, env: com.histmersion.server.GazetteerPaths.OfEnvArg1 => Option[com.histmersion.server.GazetteerPaths.OfEnvResult], osName: com.histmersion.server.GazetteerPaths.OfOsName): GazetteerPaths =
    val data = UserPaths
      .dataDir(cfg.runtime.dataDir, env, osName)
      .getOrElse(Path.of(cfg.init.dataDir))
      .resolve("gazetteer")
    val cache = UserPaths.cacheDir(env).map(_.resolve("gazetteer")).getOrElse(data)
    def or(configured: com.histmersion.server.GazetteerPaths.OfOrConfigured, fallback: Path): Path =
      Option(configured)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(p => Path.of(com.histmersion.domain.automation.agents.AgentTrees.expandIn(p, env)))
        .getOrElse(fallback)
    val extracts = or(cfg.places.sources.dir, cache)
    GazetteerPaths(
      extracts,
      or(cfg.places.sources.lpfDir, data.resolve("lpf")),
      Path.of(cfg.init.dataDir).resolve(cfg.places.sources.manifest),
      Option.when(data != extracts)(data)
    )
