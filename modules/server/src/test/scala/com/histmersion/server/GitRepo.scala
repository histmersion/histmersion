package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import scala.jdk.CollectionConverters.*
import scala.util.Try

final case class GitRepo(repo: Path):
  def run(args: String*): (Int, String) =
    val builder = new ProcessBuilder(("git" :: "-C" :: repo.toString :: args.toList).asJava)
      .redirectError(ProcessBuilder.Redirect.DISCARD)
    Try {
      val process = builder.start()
      val out = new String(process.getInputStream.readAllBytes(), UTF_8)
      (process.waitFor(), out)
    }.getOrElse((-1, ""))

  def ok(args: String*): Option[String] =
    val (code, out) = run(args*)
    Option.when(code == 0)(out)

  def isRepository: Boolean = ok("rev-parse", "--is-inside-work-tree").exists(_.trim == "true")

  def show(commit: String, path: String): Option[List[String]] =
    ok("cat-file", "-e", s"$commit:$path").flatMap(_ => ok("show", s"$commit:$path")).map(_.linesIterator.toList)

  def base(env: Map[String, String]): Either[String, MergeBase] =
    if !isRepository then
      Left(
        s"not a git repository (${repo.toAbsolutePath.normalize}): the comparison is with the merge base with " +
          s"${MergeBase.Defaults.head}; run the suites from a clone that has that branch"
      )
    else
      def mergeBase(ref: String): Option[MergeBase] =
        ok("merge-base", "HEAD", ref).map(_.trim).filter(_.nonEmpty).map(MergeBase(_, ref))
      env.get(MergeBase.Variable).filter(_.nonEmpty) match
        case Some(ref) =>
          mergeBase(ref).toRight(s"${MergeBase.Variable}=$ref: no merge base of HEAD and $ref")
        case None =>
          MergeBase.Defaults.iterator
            .flatMap(mergeBase)
            .nextOption()
            .toRight(
              s"no merge base of HEAD and ${MergeBase.Defaults.mkString(" or ")} (a shallow clone, or the branch " +
                s"not fetched): fetch it, or name the base in ${MergeBase.Variable}"
            )
