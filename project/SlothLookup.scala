import java.io.File

/** Where the build looks for a Sloth CLI jar (#874), first match wins:
  *   1. `SLOTH_JAR`;
  *   2. a Sloth checkout next to THIS checkout (`<repo>/../sloth`), as CI makes one;
  *   3. a Sloth checkout next to the MAIN worktree, when this checkout is a linked `git worktree`
  *      (`git rev-parse --git-common-dir` names the main worktree's `.git`; its parent is the main
  *      worktree). A worktree under `histmersion-wt/<branch>` has no `../sloth` of its own, so the
  *      native image used to build unpatched there.
  * The paths are computed here (pure); finding the jar inside each checkout is the build's. */
object SlothLookup {

  /** The Sloth checkouts to look in, in order, without duplicates.
    *
    * @param repo         this checkout's root
    * @param gitCommonDir what `git rev-parse --git-common-dir` printed there (relative to `repo` or
    *                     absolute), if git answered */
  def checkouts(repo: File, gitCommonDir: Option[String]): Seq[File] = {
    val here = repo.getAbsoluteFile
    val main = gitCommonDir.map(_.trim).filter(_.nonEmpty).map { d =>
      val f = new File(d)
      (if (f.isAbsolute) f else new File(here, d)).getCanonicalFile.getParentFile
    }
    (Seq(here) ++ main).map(r => new File(r.getCanonicalFile.getParentFile, "sloth")).distinct
  }

  /** `git rev-parse --git-common-dir` in `repo`; `None` when git is missing or this is no checkout. */
  def gitCommonDir(repo: File): Option[String] =
    scala.util.Try {
      val out = new StringBuilder
      val rc  = scala.sys.process.Process(Seq("git", "rev-parse", "--git-common-dir"), repo)
        .!(scala.sys.process.ProcessLogger(l => out.append(l), _ => ()))
      if (rc == 0) Some(out.toString.trim) else None
    }.toOption.flatten
}
