import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

/** G111 (owner decision E15): a CLI failure's exit code is worked out from its problem, never given at the site. This
  * rule drops the explicit code from every `Cli.Error(...)` (and `Error(...)` inside `object Cli`): the named argument
  * `exitCode = <anything>` and a positional second argument `ExitCode.<Name>`.
  *
  * A site whose problem is written in place as `Problem.Unclassified(...)` and whose code is not `ExitCode.Usage` would
  * change its code when the argument goes (a local unclassified problem exits as a usage error): it is listed as
  * `retype` and left alone, for a hand edit (`Cli.Error.answered`, or a typed case). A code that is not a literal
  * `ExitCode.<Name>` (a variable, an `if`) is listed as `by-hand` and left alone too.
  *
  * Modes: `dry-run` (the default) only writes the listing; `apply` also drops the arguments of the `drop` rows. Re-run
  * after an apply, the listing is empty. Run from the worktree root:
  *   bash tools/refactor/scalafix.sh --config tools/refactor/drop-cli-exit-code.conf --files modules/cli/src
  *   bash tools/refactor/scalafix.sh --config tools/refactor/drop-cli-exit-code-apply.conf --files modules/cli/src
  * then `cli/scalafmtOnly` on the changed files.
  */
object DropCliExitCode {
  final case class Policy(mode: String = "dry-run", report: String = "refactor-audit/cli-exit-code.tsv")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}

class DropCliExitCode(val policy: DropCliExitCode.Policy) extends SyntacticRule("DropCliExitCode") {
  def this() = this(DropCliExitCode.Policy())
  import DropCliExitCode._
  private val rows = new ConcurrentLinkedQueue[List[String]]()

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("DropCliExitCode")(Policy()).andThen { p =>
      if (!Set("dry-run", "apply")(p.mode)) Configured.error("mode is dry-run or apply")
      else Configured.ok(new DropCliExitCode(p))
    }

  private def insideCli(t: Tree): Boolean = t.parent.exists {
    case o: Defn.Object if o.name.value == "Cli" => true
    case p                                      => insideCli(p)
  }

  private def isError(fun: Term): Boolean = fun match {
    case Term.Select(Term.Name("Cli"), Term.Name("Error")) => true
    case n @ Term.Name("Error")                            => insideCli(n)
    case _                                                 => false
  }

  /** `ExitCode.<Name>`: the literal code's name. */
  private def literal(t: Term): Option[String] = t match {
    case Term.Select(Term.Name("ExitCode"), Term.Name(n))                                     => Some(n)
    case Term.Select(Term.Select(Term.Name("Error"), Term.Name("ExitCode")), Term.Name(n)) => Some(n)
    case _                                                                                    => None
  }

  private def unclassifiedInPlace(t: Term): Boolean = t match {
    case Term.Apply.After_4_6_0(Term.Select(Term.Name("Problem"), Term.Name("Unclassified")), _) => true
    case _                                                                                      => false
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _)        => p.toString
      case Input.VirtualFile(p, _) => p
      case x                       => x.toString
    }
    lazy val tokens = doc.tree.tokens
    doc.tree.collect {
      case a @ Term.Apply.After_4_6_0(fun, clause) if isError(fun) =>
        val args = clause.values
        val coded = args.zipWithIndex.collectFirst {
          case (Term.Assign(Term.Name("exitCode"), code), i) => (i, code)
          case (t: Term, 1) if literal(t).isDefined          => (1, t)
        }
        coded match {
          case None => Patch.empty
          case Some((i, code)) =>
            val name = literal(code)
            val problem = args.headOption.collect { case t: Term => t }
            val action =
              if (i == 0) "by-hand"
              else if (name.isEmpty) "by-hand"
              else if (name.exists(_ != "Usage") && problem.exists(unclassifiedInPlace)) "retype"
              else "drop"
            rows.add(List(path, (a.pos.startLine + 1).toString, code.syntax, action, a.syntax.replace("\n", " ")))
            if (action == "drop" && policy.mode == "apply") {
              val from = args(i - 1).pos.end
              val to = args(i).pos.end
              Patch.removeTokens(tokens.filter(t => t.start >= from && t.end <= to)).atomic
            } else Patch.empty
        }
    }.asPatch
  }

  override def afterComplete(): Unit = {
    def esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r")
    val all = rows.asScala.toList.sortBy(r => (r.head, r(1).toInt))
    val p = Paths.get(policy.report)
    Files.createDirectories(p.toAbsolutePath.getParent)
    Files.write(p, ("path\tline\tcode\taction\tsite" :: all.map(_.map(esc).mkString("\t"))).asJava, UTF_8)
    val byAction = all.groupBy(_(3)).map { case (k, v) => s"$k=${v.size}" }.toList.sorted
    val summary = s"mode=${policy.mode}" :: s"sites=${all.size}" :: byAction
    Files.write(Paths.get(policy.report + ".summary"), summary.asJava, UTF_8)
    all.filter(_(3) != "drop").foreach(r => println(s"${r(3)}: ${r.head}:${r(1)} ${r(2)}"))
    println(summary.mkString(" "))
  }
}
