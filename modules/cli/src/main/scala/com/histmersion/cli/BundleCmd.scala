package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.config.*

import java.nio.file.{Files, Path, StandardCopyOption}
import com.histmersion.util.data.exchange.{BundleFormat, BundleTar}
import com.histmersion.api.exchange.{Bundle, BundleMarkdown, MergeImportRequest}
import com.histmersion.util.console.Console
import com.histmersion.api.library.InstanceLabel
import com.histmersion.util.problem.Problems

/** `histmersion bundle export|verify|import|instance` — the portable bundle (docs/research-merge-import.md, milestones
  * M0–M2). The server builds and reads bundles; the CLI moves the file: an export is streamed to disk, a verify/import
  * uploads it as the request body.
  */
object BundleCmd:

  private def existing(file: String): Either[Problems, java.io.File] =
    val p = Path.of(file)
    if Files.isRegularFile(p) then Right(p.toFile)
    else Left(Problem.Unclassified(Legacy.Text(s"no such file: $file")))

  /** The manifest of a bundle on disk — its first entry. */
  private[cli] def manifestOf(p: Path): Either[Problem, Bundle.Manifest] =
    val in = Files.newInputStream(p)
    try
      val r = BundleTar.Reader(java.io.BufferedInputStream(in))
      r.next().flatMap {
        case Some(e) if e.name.value == Bundle.ManifestPath => BundleFormat.parseManifest(r.readAll())
        case _ => Left(Problem.Unclassified(Legacy.Text(s"$p does not start with ${Bundle.ManifestPath}")))
      }
    finally in.close()

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case Export(file: String, since: Option[String])
    case Verify(file: String, json: Cli.JsonOutput)
    case Import(file: String, dryRun: Boolean, res: MergeCmd.ResolutionFlags, json: Cli.JsonOutput)
    case Instance(label: Option[String])

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.Export(file, since) =>
          val dest = Path.of(file).toAbsolutePath
          val part = dest.resolveSibling(dest.getFileName.toString + ".part")
          Option(dest.getParent).foreach(d => Files.createDirectories(d))
          c.exportBundle(since.map(Bundle.Name(_)), part.toFile) match
            case Left(e) =>
              val _ = Files.deleteIfExists(part)
              Left(e)
            case Right(_) =>
              val _ = Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
              manifestOf(dest).fold(
                e =>
                  Left(
                    Cli.Error(
                      Problem.Unclassified(
                        Legacy.Text(s"the server sent something that is not a bundle: ${e.sentence}")
                      ),
                      exitCode = ExitCode.Refused
                    )
                  ),
                m => Right(Cli.out.println(s"${BundleMarkdown.manifest(m)}\n\n→ $dest (${Files.size(dest)} bytes)"))
              )
        case Command.Verify(file, json) =>
          existing(file).left.map(Cli.Error(_, exitCode = ExitCode.Usage)).flatMap(c.verifyBundle).flatMap { r =>
            Output.emit(json, r)(BundleMarkdown.verify)
            if r.ok.value then Right(())
            else
              Left(
                Cli.Error(Problem.Unclassified(Legacy.Text("the bundle did not verify")), exitCode = ExitCode.Refused)
              )
          }
        case Command.Import(file, dryRun, res, json) =>
          val run: Either[Cli.Error, Bundle.ImportResult] =
            if res.isEmpty then
              existing(file).left
                .map(Cli.Error(_, exitCode = ExitCode.Usage))
                .flatMap(c.importBundle(Cli.DryRun(dryRun), _))
            else
              for
                f <- existing(file).left.map(Cli.Error(_, exitCode = ExitCode.Usage))
                r <- MergeCmd.resolutions(res).left.map(Cli.Error(_, exitCode = ExitCode.Usage))
                u <- MergeCmd.upload(c, f)
                x <- c.mergeImport(
                  MergeImportRequest(MergeImportRequest.Bundle(u.sha256.value), r, MergeImportRequest.DryRun(dryRun))
                )
              yield x
          run.flatMap { r =>
            Output.emit(json, r)(BundleMarkdown.importResult)
            if r.problems.isEmpty then Right(())
            else
              Left(Cli.Error(Problem.Unclassified(Legacy.Text("the import was refused")), exitCode = ExitCode.Refused))
          }
        case Command.Instance(label) =>
          label
            .fold(c.instance)(l => c.setInstance(Some(InstanceLabel.Label(l))))
            .map(i => Cli.out.println(BundleMarkdown.instance(i)))
