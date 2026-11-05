package com.histmersion.util.storage
import com.histmersion.api.given

import java.nio.file.{Path, Paths}

/** Confining a caller-supplied path to a configured directory.
  *
  * Three operations take a server-side path from whoever is calling: `POST /api/seed {dir}`, the MCP `ingest {path}`
  * tool, and a source note's `md_path`. None of them checked it, so "the server" was a file-read primitive for anything
  * that could reach those surfaces — `md_path: ../../../../etc` being the quiet one, since a note is content an agent
  * writes.
  *
  * `corpus.dir` is the configured root of the material the server is allowed to read. When it is set, a supplied path
  * must resolve inside it. When it is EMPTY (the default, and the host-side workflow where the server sees the user's
  * own filesystem) the behaviour is unchanged and the caller logs a warning — silently tightening it would break every
  * existing deployment.
  */
object SafePath:

  /** `Right(resolved)` when `candidate` is inside `base`, or when `base` is not configured. Relative candidates are
    * resolved against `base` when there is one.
    */
  def confine(base: Option[String], candidate: String): Either[String, Path] =
    val root = base.map(_.trim).filter(_.nonEmpty)
    val raw = Option(candidate).map(_.trim).getOrElse("")
    if raw.isEmpty then Left("no path given")
    else
      root match
        case None      => Right(Paths.get(raw).toAbsolutePath.normalize)
        case Some(dir) =>
          val b = Paths.get(dir).toAbsolutePath.normalize
          val p = Paths.get(raw) match
            case r if r.isAbsolute => r.normalize
            case r                 => b.resolve(r).toAbsolutePath.normalize
          if p.startsWith(b) then Right(p)
          else Left(s"'$candidate' is outside the configured corpus directory ($dir)")

  /** The same test for an already-built [[Path]] — what the frontmatter reader needs. */
  def within(base: Option[String], candidate: Path): Boolean =
    base.map(_.trim).filter(_.nonEmpty) match
      case None      => true
      case Some(dir) =>
        candidate.toAbsolutePath.normalize.startsWith(Paths.get(dir).toAbsolutePath.normalize)

  /** Whether confinement is switched on at all — the caller logs a warning when it is not. */
  def configured(base: Option[String]): Boolean = base.exists(_.trim.nonEmpty)
