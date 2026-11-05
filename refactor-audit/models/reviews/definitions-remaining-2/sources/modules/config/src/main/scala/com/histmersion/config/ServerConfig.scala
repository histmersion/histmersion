package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Application config, modelled as case classes and read from HOCON (sconfig) via Kindlings derivation. Defaults + env
  * overrides live in `reference.conf`; nothing is hardcoded elsewhere.
  */
/** `requestTimeoutSeconds` bounds one HTTP request end to end. Netty's default is 20 s, which is shorter than a
  * `pg_dump` of the live corpus (~40 s for 860 MB) — the request was answered 503 and the finished dump was never moved
  * into place. Long operations (backup, restore, seed, ingest of a large file) need this to be at least
  * `backups.timeout-seconds`. `shutdownGraceSeconds` is how long a stop waits for the open connections to close — netty
  * counts an idle keep-alive connection as open, so every stop with a client still connected waited the whole time; the
  * test suites set it to 0.
  */
/** `sourceUrl`: the repository the About window and `histmersion about` link as this program's source. */
final case class ServerConfig(
    host: ServerConfig.Host,
    port: ServerConfig.Port,
    publicUrl: ServerConfig.PublicUrl,
    requestTimeoutSeconds: ServerConfig.RequestTimeoutSeconds,
    shutdownGraceSeconds: ServerConfig.ShutdownGraceSeconds,
    sourceUrl: ServerConfig.SourceUrl,
    restartExitCode: ServerConfig.RestartExitCode = 75
) derives ConfigReader

object ServerConfig:
  type Host = Host.T
  object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Port = Port.T
  object Port extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PublicUrl = PublicUrl.T
  object PublicUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestTimeoutSeconds = RequestTimeoutSeconds.T
  object RequestTimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShutdownGraceSeconds = ShutdownGraceSeconds.T
  object ShutdownGraceSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceUrl = SourceUrl.T
  object SourceUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestartExitCode = RestartExitCode.T
  object RestartExitCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
