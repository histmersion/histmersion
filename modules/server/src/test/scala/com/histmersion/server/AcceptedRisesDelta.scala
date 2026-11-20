package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Try

import com.histmersion.server.RatchetBase.Accepted

/** Rewrites the accepted-rises file into the delta form ([[Accepted.rewrite]]): each first-form row (from, to) as its
  * delta `to - from`, the rows' order and values kept, so the rewrite grants no branch a new approval. The owner's
  * accept script runs it from the repository root (agents may not write the file):
  * {{{
  *   sbt "server/Test/runMain com.histmersion.server.AcceptedRisesDelta"            # architecture/accepted-rises.tsv
  *   sbt "server/Test/runMain com.histmersion.server.AcceptedRisesDelta <file>"
  * }}}
  * A malformed row stops it before anything is written.
  */
object AcceptedRisesDelta:

  /** Rewrites `file` in place: the number of rows, or why nothing was written. */
  def rewriteFile(file: Path): Either[String, Int] =
    for
      lines <- Try(Files.readAllLines(file, UTF_8).asScala.toList).toEither.left.map(e =>
        s"$file is unreadable: ${e.getMessage}"
      )
      out <- Accepted.rewrite(lines)
      _ <- Try(Files.write(file, out.asJava, UTF_8)).toEither.left.map(e => s"$file is not writable: ${e.getMessage}")
    yield out.size - 1

  def main(args: Array[String]): Unit =
    val file = Path.of(args.headOption.getOrElse(RatchetBase.AcceptedFile))
    rewriteFile(file) match
      case Right(n)  => println(s"$file: $n rows, in the delta form")
      case Left(why) => throw IllegalStateException(why)
