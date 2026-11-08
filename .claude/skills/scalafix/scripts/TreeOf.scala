//> using scala 2.13.18
//> using dep org.scalameta::scalameta:4.17.4

import scala.meta._

/** Prints the Scalameta tree of a code snippet. Run it with tree-of.sh. */
object TreeOf {
  def main(args: Array[String]): Unit = {
    val (flags, rest) = args.toList.partition(_.startsWith("--"))
    val dialect = if (flags.contains("--scala3")) dialects.Scala3 else dialects.Scala213
    val code = rest.mkString(" ")
    implicit val d: Dialect = dialect

    // A snippet is usually an expression or a statement; fall back to a whole source file.
    val parsed: Either[String, Tree] =
      Seq[String => Parsed[Tree]](_.parse[Term], _.parse[Stat], _.parse[Source])
        .map(_(code))
        .collectFirst { case Parsed.Success(t) => t }
        .toRight(code.parse[Source] match {
          case e: Parsed.Error => e.toString
          case _ => "parse error"
        })

    parsed match {
      case Left(error) =>
        System.err.println(error)
        sys.exit(1)
      case Right(tree) =>
        println(pretty(tree))
        if (flags.contains("--tokens"))
          println(tree.tokens.map(t => s"${t.productPrefix}(${t.syntax.replace("\n", "\\n")})").mkString("\ntokens: ", " ", ""))
    }
  }

  private def pretty(t: Tree, depth: Int = 0): String = {
    // Recent Scalameta versions print `structure` over many lines; join it and break it only where it's too long.
    val flat = t.structure.replaceAll("\\(\\s*\n\\s*", "(").replaceAll(",\\s*\n\\s*", ", ").replaceAll("\\s*\n\\s*\\)", ")")
    if (flat.length + depth * 2 <= 100 || t.children.isEmpty) flat
    else {
      val pad = "  " * (depth + 1)
      t.children.map(c => pad + pretty(c, depth + 1)).mkString(s"${t.productPrefix}(\n", ",\n", ")")
    }
  }
}
