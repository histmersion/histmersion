import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

/** G58 slice 2: SQL text a helper built as a String, and spliced as `${SqlLiteral(helper(...))}`, becomes a `Frag` the
  * helper builds from `Frag` columns. The helpers' definitions are changed by hand (they return `Frag` and take `Frag`
  * columns); this rule follows at their call sites:
  *
  *   - `calls`: a call of a configured function (`fun`, matched on its spelling: `RangeSql.start`, `ts`) has each
  *     String literal at the positions `sqlArgs` written as a `sql"..."` fragment of the same text (`"f.period"` ->
  *     `sql"f.period"`), and is renamed to `to` when given (`ts(...)` -> `Timestamps.iso(...)`). Wrapped in
  *     `SqlLiteral(...)`, the wrapper goes: the call is a `Frag` now and nests in place. `imports` are added to a file
  *     where a call was rewritten.
  *   - `names`: `SqlLiteral(x)`, where `x` (or `a.b.x`) is a configured name whose definition is a `Frag` now, becomes
  *     `x`.
  *
  * `files` restricts an entry to the files whose path ends with one of them (the short names `t`, `at`, `cols` mean
  * different things in different files). The `SqlLiteral` importee goes where none is left; `sql` is imported where a
  * fragment was written and the file had no `sql` in scope. What it cannot rewrite (a non-literal argument at an SQL
  * position, a triple-quoted literal with a backslash, which `sql"""..."""` would read as an escape, a bare call
  * spliced into an `s"..."` or joined with `+`, where a `Frag` would put its `toString` into the text) is kept and
  * listed in `report` for the hand fixes. Syntactic: what the compiler then refuses is fixed by hand.
  */
object Magnum3Frag {
  case class Call(fun: String, to: String = "", sqlArgs: List[Int] = List(0), files: List[String] = Nil, imports: List[String] = Nil)
  object Call {
    implicit val surface: generic.Surface[Call] = generic.deriveSurface[Call]
    implicit val decoder: ConfDecoder[Call] = generic.deriveDecoder(Call("")).noTypos
  }
  case class Splice(name: String, files: List[String] = Nil)
  object Splice {
    implicit val surface: generic.Surface[Splice] = generic.deriveSurface[Splice]
    implicit val decoder: ConfDecoder[Splice] = generic.deriveDecoder(Splice("")).noTypos
  }
  case class Config(calls: List[Call] = Nil, names: List[Splice] = Nil, report: String = "")
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
  val Magnum = "com.augustnagro.magnum"
}

class Magnum3Frag(config: Magnum3Frag.Config) extends SyntacticRule("Magnum3Frag") {
  import Magnum3Frag._

  def this() = this(Magnum3Frag.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("Magnum3Frag")(this.config).map(new Magnum3Frag(_))

  private def inFile(path: String, files: List[String]): Boolean = files.isEmpty || files.exists(f => path.endsWith(f))

  private def lastName(t: Term): Option[String] = t match {
    case Term.Name(n)      => Some(n)
    case Term.Select(_, n) => Some(n.value)
    case _                 => None
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.VirtualFile(p, _) => p
      case Input.File(p, _)        => p.toString
      case other                   => other.syntax
    }
    val calls = config.calls.filter(c => inFile(path, c.files))
    val names = config.names.filter(n => inFile(path, n.files)).map(_.name).toSet
    val left = List.newBuilder[String]
    var wroteFrag = false
    val imports = scala.collection.mutable.LinkedHashSet.empty[String]

    def callOf(a: Term.Apply): Option[Call] = calls.find(_.fun == a.fun.syntax)

    /** A String literal as the `sql"..."` of the same text, or None (with the reason) when that would change it. */
    def asFrag(l: Lit.String): Either[String, String] = {
      val src = l.syntax
      val triple = src.startsWith("\"\"\"")
      if (triple && src.contains("\\")) Left("triple-quoted literal with a backslash")
      else {
        wroteFrag = true
        Right("sql" + src.replace("$", "$$"))
      }
    }

    def line(t: Tree): String = s"${path.split('/').last}:${t.pos.startLine + 1}"

    /** The call with its SQL arguments as fragments and its new name; its other arguments as they are. */
    def render(a: Term.Apply, c: Call): String = {
      val args = a.argClause.values.zipWithIndex.map { case (arg, i) =>
        arg match {
          case l: Lit.String if c.sqlArgs.contains(i) =>
            asFrag(l) match {
              case Right(f) => f
              case Left(why) => left += s"${line(arg)}\t${c.fun}\t$why"; arg.syntax
            }
          case s: Term.Interpolate if s.prefix.value == "sql" => s.syntax
          case other if c.sqlArgs.contains(i) =>
            left += s"${line(arg)}\t${c.fun}\tnot a literal at an SQL position: ${other.syntax}"; other.syntax
          case other => other.syntax
        }
      }
      imports ++= c.imports
      val fun = if (c.to.nonEmpty) c.to else a.fun.syntax
      s"$fun(${args.mkString(", ")})"
    }

    def isSqlLiteral(f: Term): Boolean = f match {
      case Term.Name("SqlLiteral")                       => true
      case Term.Select(_, Term.Name("SqlLiteral"))       => true
      case _                                             => false
    }

    val wrapped = scala.collection.mutable.Set.empty[Tree]
    val outer = doc.tree.collect {
      case w @ Term.Apply.After_4_6_0(f, Term.ArgClause(List(inner), _)) if isSqlLiteral(f) =>
        inner match {
          case a: Term.Apply if callOf(a).isDefined =>
            wrapped += a
            Patch.replaceTree(w, render(a, callOf(a).get))
          case n if lastName(n).exists(names.contains) && (n.is[Term.Name] || n.is[Term.Select]) =>
            Patch.replaceTree(w, n.syntax)
          case _ => Patch.empty
        }
    }
    // calls not wrapped in SqlLiteral, and not inside one already rewritten as a whole
    def insideWrapped(t: Tree): Boolean = t.parent.exists(p => wrapped.contains(p) || insideWrapped(p))
    // A bare call spliced into text (an `s"..."`, a `+`) stays: a Frag there would put its toString into the SQL.
    def inText(t: Tree): Boolean = t.parent match {
      case Some(b: Term.Block)                              => inText(b)
      case Some(c: Term.ArgClause) if c.parent.exists(_.is[Term.ApplyInfix]) => inText(c)
      case Some(i: Term.Interpolate)                        => i.prefix.value != "sql"
      case Some(Term.ApplyInfix.After_4_6_0(_, op, _, _)) => op.value == "+"
      case _                                                => false
    }
    val bare = doc.tree.collect {
      case a: Term.Apply if callOf(a).isDefined && !wrapped.contains(a) && !insideWrapped(a) && inText(a) =>
        left += s"${line(a)}\t${a.fun.syntax}\tspliced into text, kept: ${a.syntax}"
        Patch.empty
      case a: Term.Apply if callOf(a).isDefined && !wrapped.contains(a) && !insideWrapped(a) =>
        val c = callOf(a).get
        val before = a.syntax
        val after = render(a, c)
        if (after == before) Patch.empty else Patch.replaceTree(a, after)
    }

    val rewritten = (outer ++ bare).filterNot(_.isEmpty)
    // every mention of SqlLiteral outside the imports, less the wrappers this run removes
    val remainingLiterals = doc.tree.collect {
      case t @ Term.Name("SqlLiteral") if !t.parent.exists(_.is[Importee]) => t
    }.size - outer.count(!_.isEmpty)
    val magnumImporters = doc.tree.collect { case i: Importer if i.ref.syntax == Magnum => i }
    val dropLiteral =
      if (rewritten.nonEmpty && remainingLiterals == 0)
        magnumImporters.flatMap(_.importees.collect {
          case i @ Importee.Name(Name.Indeterminate("SqlLiteral")) => Patch.removeImportee(i)
        }).asPatch
      else Patch.empty
    val hasSql = magnumImporters.exists(_.importees.exists {
      case Importee.Name(Name.Indeterminate("sql")) => true
      case _: Importee.Wildcard                     => true
      case _                                        => false
    })
    val addSql =
      if (wroteFrag && rewritten.nonEmpty && !hasSql) Patch.addGlobalImport(importer"com.augustnagro.magnum.sql")
      else Patch.empty
    val present = doc.tree.collect { case i: Importer => i.syntax }.toSet
    val addOthers =
      if (rewritten.isEmpty) Patch.empty
      else imports.toList.filterNot(present.contains).map { s =>
        s.parse[Importer] match {
          case Parsed.Success(imp) => Patch.addGlobalImport(imp)
          case _                   => Patch.empty
        }
      }.asPatch

    val notes = left.result()
    if (config.report.nonEmpty && notes.nonEmpty) {
      val out = java.nio.file.Paths.get(config.report)
      Option(out.getParent).foreach(java.nio.file.Files.createDirectories(_))
      java.nio.file.Files.write(
        out,
        notes.map(_ + "\n").mkString.getBytes("UTF-8"),
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND
      )
    }
    rewritten.asPatch + dropLiteral + addSql + addOthers
  }
}
