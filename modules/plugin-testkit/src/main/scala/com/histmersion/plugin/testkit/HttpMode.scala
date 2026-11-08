package com.histmersion
package plugin.testkit

import com.histmersion.plugin.testkit.internal.ProjectPaths
import com.histmersion.pluginhost.HttpTransport

import java.nio.file.Path
import io.github.iltotore.iron.constraint.any.Pure

/** Where a [[PluginHarness]]'s requests go. Whatever the mode, every request passes through the server's own HTTP
  * policy first — its User-Agent, its timeouts and TLS trust, `Retry-After`, bot challenges ending the request — so a
  * plugin sees exactly what the server would show it; only the last step, the wire, differs.
  *
  * {{{
  * val mode = HttpMode.fromEnv(Path.of("recordings", "known-book"))
  * mode.left.foreach(why => println(s"SKIPPED: $why"))   // munit reports a skip without its reason
  * assume(mode.isRight, mode.left.getOrElse(""))
  * val harness = PluginHarness.load("my-plugin", config, http = mode.toOption.get)
  * }}}
  */
enum HttpMode:
  /** The real network, nothing kept. */
  case Live

  /** The real network, and every exchange written to `dir` (replacing what a previous recording left there): one
    * `NNN-<host>.http` file per exchange — method, URL, status and a safe subset of headers — and its body beside it in
    * a file of its own. Never written: `Authorization`, cookies, any other header outside that subset, and the value of
    * any secret the harness knows (in a URL, a header or a text body it reads `{secret}`). One harness per directory.
    */
  case Record(dir: Path)

  /** No network: each request is answered from what [[Record]] wrote to `dir`, in the order it was recorded (a request
    * asked more often than recorded gets its last answer again). A request with no recording answers nothing to the
    * plugin, and the runner that made it then fails the test with [[UnrecordedRequest]], naming the URL. Waits (a
    * recorded `Retry-After`, a collection's pace) are skipped.
    */
  case Replay(dir: Path)

  /** No network: canned answers by exact URL, for unit tests; any other URL answers HTTP 404. */
  case Stub(answers: Map[HttpTransport.Url, StubAnswer])

  /** Whether requests reach the real network. */
  def live: Boolean = this match
    case Live | Record(_) => true
    case _                => false

object HttpMode:
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  val ModeVariable = "HISTMERSION_PLUGIN_HTTP"

  /** Set (`1`, `true`, `yes`): a test with no recordings yet may go to the network. */
  val LiveVariable = "HISTMERSION_PLUGIN_LIVE"

  /** The system property naming the plugin project's directory, against which [[fromEnv]] resolves a relative
    * recordings directory: `//> using test.javaProp histmersion.plugin.project=${.}` in a scala-cli build,
    * `-Dhistmersion.plugin.project=<base directory>` in a forked sbt test JVM. Unset, a relative directory is taken
    * from the working directory.
    */
  val ProjectProperty = "histmersion.plugin.project"

  /** Every request answers HTTP 404: a test that must not touch the network. */
  val Offline: HttpMode = Stub(Map.empty)

  /** The mode a test of one recorded case runs in, from the environment. A relative `dir` is resolved against the
    * project directory [[ProjectProperty]] names (else the working directory), so a recorded case is found wherever the
    * build was started; the modes and the reason to skip carry that path.
    *
    *   - `HISTMERSION_PLUGIN_HTTP=live|record|replay` decides, when set;
    *   - otherwise [[Replay]] when `dir` holds recordings — offline, in CI too;
    *   - otherwise [[Live]] when `HISTMERSION_PLUGIN_LIVE` is set;
    *   - otherwise `Left`, the reason to skip the test: there is nothing to replay, and going to the network was not
    *     asked for.
    */
  def fromEnv(dir: Path, env: Map[String, String] = sys.env): Either[Problem, HttpMode] =
    choose(ProjectPaths.resolve(dir, sys.props.get(ProjectProperty).map(ProjectPaths.ProjectDir(_))), env)

  private def choose(dir: Path, env: Map[String, String]): Either[Problem, HttpMode] =
    env.get(ModeVariable).map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
      case Some("live")                  => Right(Live)
      case Some("record")                => Right(Record(dir))
      case Some("replay")                => Right(Replay(dir))
      case Some(other)                   => Left(Problem(s"$ModeVariable=$other: one of live, record, replay"))
      case None if Recordings.exist(dir) => Right(Replay(dir))
      case None
          if env.get(LiveVariable).exists(v => Set("1", "true", "yes")(v.trim.toLowerCase(java.util.Locale.ROOT))) =>
        Right(Live)
      case None =>
        Left(
          Problem(
            s"no recordings in $dir: set $LiveVariable=1 to run against the real site " +
              s"($ModeVariable=record to keep its answers for an offline replay)"
          )
        )
