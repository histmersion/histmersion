package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.files.*
import com.histmersion.config.*

import java.nio.file.{Files, Path, StandardCopyOption}
import com.histmersion.util.data.exchange.{BundleFormat, BundleTar}

/** `histmersion bundle export|verify|import|instance` — the portable bundle (docs/research-merge-import.md, milestones
  * M0–M2). The server builds and reads bundles; the CLI moves the file: an export is streamed to disk, a verify/import
  * uploads it as the request body.
  */
object BundleCmd:

  private def existing(file: String): Either[CliError, java.io.File] =
    val p = Path.of(file)
    if Files.isRegularFile(p) then Right(p.toFile)
    else Left(CliError(s"no such file: $file", exitCode = ExitCode.Usage))

  /** The manifest of a bundle on disk — its first entry. */
  private[cli] def manifestOf(p: Path): Either[String, BundleManifest] =
    val in = Files.newInputStream(p)
    try
      val r = BundleTar.Reader(java.io.BufferedInputStream(in))
      r.next().flatMap {
        case Some(e) if e.name == Bundle.ManifestPath => BundleFormat.parseManifest(r.readAll())
        case _                                        => Left(s"$p does not start with ${Bundle.ManifestPath}")
      }
    finally in.close()

  private val exportCmd = Opts.subcommand(
    "export",
    "Write the whole database (or, with --since, what changed after a moment, deletions included) to a portable .hmx bundle"
  ) {
    (
      Opts.argument[String]("file.hmx"),
      Opts
        .option[String]("since", "Only what changed after this moment (ISO-8601, e.g. a previous bundle's asOf)")
        .orNone
    ).mapN { (file, since) => (c: Client) =>
      val dest = Path.of(file).toAbsolutePath
      val part = dest.resolveSibling(dest.getFileName.toString + ".part")
      Option(dest.getParent).foreach(d => Files.createDirectories(d))
      c.exportBundle(since, part.toFile) match
        case Left(e) =>
          val _ = Files.deleteIfExists(part)
          Left(e)
        case Right(_) =>
          val _ = Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
          manifestOf(dest).fold(
            e => Left(CliError(s"the server sent something that is not a bundle: $e", exitCode = ExitCode.Refused)),
            m => Right(println(s"${BundleMarkdown.manifest(m)}\n\n→ $dest (${Files.size(dest)} bytes)"))
          )
    }
  }

  private val verifyCmd = Opts.subcommand(
    "verify",
    "Check a bundle against this server: format, schema, every checksum, record and blob — every problem listed"
  ) {
    (Opts.argument[String]("file.hmx"), Output.jsonFlag).mapN { (file, json) => (c: Client) =>
      existing(file).flatMap(c.verifyBundle).flatMap { r =>
        Output.emit(json, r)(BundleMarkdown.verify)
        if r.ok then Right(()) else Left(CliError("the bundle did not verify", exitCode = ExitCode.Refused))
      }
    }
  }

  private val importCmd = Opts.subcommand(
    "import",
    "Import a bundle: what the merge plan says is new or changed, after every open issue is decided (--resolutions); refused while one is open"
  ) {
    (
      Opts.argument[String]("file.hmx"),
      Opts.flag("dry-run", "Report what the import would do, and write nothing").orFalse,
      MergeCmd.resolutionOpts,
      Output.jsonFlag
    ).mapN { (file, dryRun, res, json) => (c: Client) =>
      val run: Either[CliError, BundleImportResult] =
        if res.isEmpty then existing(file).flatMap(c.importBundle(dryRun, _))
        else
          for
            f <- existing(file)
            r <- MergeCmd.resolutions(res)
            u <- MergeCmd.upload(c, f)
            x <- c.mergeImport(MergeImportRequest(u.sha256, r, dryRun))
          yield x
      run.flatMap { r =>
        Output.emit(json, r)(BundleMarkdown.importResult)
        if r.problems.isEmpty then Right(()) else Left(CliError("the import was refused", exitCode = ExitCode.Refused))
      }
    }
  }

  private val instanceCmd = Opts.subcommand(
    "instance",
    "This collection's identity (the origin of every row it creates); --label names it, e.g. work or home"
  ) {
    Opts.option[String]("label", "Name this instance; an empty string clears the name").orNone.map {
      label => (c: Client) =>
        label.fold(c.instance)(l => c.setInstance(Some(l))).map(i => println(BundleMarkdown.instance(i)))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand(
      "bundle",
      "Portable .hmx bundles: export, verify and import a whole collection, carrying its identities"
    ) {
      exportCmd orElse verifyCmd orElse importCmd orElse instanceCmd
    }
