import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8

/** G58 (Magnum 1.3.1 -> 2.0): the db module's own fragment type `util.database.Sql` (text + `Param`s, composed with
  * `++`) becomes Magnum 2's `Frag`, which nests: a `Frag` interpolated into `sql"..."` is spliced as SQL with its
  * parameters bound in place. Run over the SemanticDB of the 1.3.1 build (the symbols of `Sql`/`Param` must resolve).
  *
  *  - `Sql.raw(Sql.Text("..."))` (also `RawText`/`OfText`, and a file's own `private def raw(text: String): Sql`)
  *    becomes `sql"..."`: constant SQL text needs no raw operator at all;
  *  - `Sql.of(Sql.Text("... ? ..."), p1, ...)` (and a file's own `of(text, ps*)`) becomes `sql"... $p1 ..."`, each `?`
  *    replaced by its parameter, in order; `Param.S(Param.S.V(e))` (L/I/D alike, also through the file's own
  *    `str`/`lng`/`int` helpers) binds `e` through its codec, `Param.A(xs.map(Param.A.V(_)))` binds `xs` as `text[]`
  *    (the `DbCodec[List[String]]` of `util.database`, imported where needed);
  *  - `a ++ b ++ c` (and `x.wrap("(", ")")`) becomes one `sql"$a$b$c"`, the constant parts merged into the text and every
  *    other operand nested as a `Frag` (kept in place, so the rewrites inside it apply too); `Sql.empty` is `sql""`;
  *  - text spliced from a String into raw SQL (`s"... $table ..."`, a non-literal text) stays raw, now visibly so:
  *    `${SqlLiteral(table)}`. A `private val X = "..."` of the same file used only as such text becomes a `Frag`
  *    (`sql"..."`) and is nested instead, as is `SqlLiteral(X)` in a `sql"..."`;
  *  - `x.frag` is `x`; `.text.value`/`.text` of a fragment are its `.sqlString`; the type `Sql` is `Frag`; the imports
  *    of `Sql`/`Param` go and Magnum's `Frag`/`sql`/`SqlLiteral` are imported where used.
  *
  * Left alone and listed in `report` (path, line, reason), for a hand fix: a placeholder operand (`_ ++ _`), a `?` count
  * that does not match the parameters (or a `??`), the `Sql(...)` constructor, `Param` values whose type is `Param`
  * (they become the codec-typed value at their source by hand), and `Sql.Text` outside an operator.
  */
object Magnum2Frag {
  case class Config(report: String = "")
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  val DbPkg = "com/histmersion/util/database/"
  val SqlCls = DbPkg + "Sql#"
  val SqlObj = DbPkg + "Sql."
  val ParamObj = DbPkg + "Param."

  /** A segment of the SQL text being built: constant text (already in `sql"..."` escaping), a nested `Frag` kept in
    * place in the source, a nested `Frag` or bound value given as code, or a raw String spliced as `SqlLiteral`.
    */
  sealed trait Seg
  final case class Text(text: String, triple: Boolean) extends Seg
  final case class Keep(t: Term) extends Seg
  final case class Code(code: String) extends Seg
  final case class Raw(code: String) extends Seg

  final class Bail(val reason: String) extends Exception(reason)
  def bail(reason: String): Nothing = throw new Bail(reason)
}

class Magnum2Frag(config: Magnum2Frag.Config) extends SemanticRule("Magnum2Frag") {
  import Magnum2Frag._

  def this() = this(Magnum2Frag.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("Magnum2Frag")(this.config).map(new Magnum2Frag(_))

  private def write(rows: List[String]): Unit = if (config.report.nonEmpty && rows.nonEmpty) this.synchronized {
    Option(Paths.get(config.report).getParent).foreach(Files.createDirectories(_))
    Files.write(
      Paths.get(config.report),
      rows.map(_ + "\n").mkString.getBytes(UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    )
  }

  override def fix(implicit doc: SemanticDocument): Patch = new Run().patch
  private final class Run(implicit doc: SemanticDocument) {
    val path: String = doc.input match {
      case Input.VirtualFile(p, _) => p
      case Input.File(p, _)        => p.toString
      case other                   => other.syntax
    }
    val rows = List.newBuilder[String]
    def note(t: Tree, reason: String): Unit = rows += List(path, (t.pos.startLine + 1).toString, reason).mkString("\t")

    def sym(t: Tree): String = t.symbol.value
    def isSql(t: Tree, member: String): Boolean = sym(t).startsWith(SqlObj + member)
    def isSqlMember(t: Tree, member: String): Boolean = sym(t).startsWith(SqlCls + member)
    def isParam(t: Tree): Boolean = sym(t).startsWith(ParamObj)

    // ---- the file's own helpers: private def raw(text: String): Sql = Sql.raw(...), of(text, ps*), str(x): Param, ...
    def body(d: Defn.Def): Term = d.body
    def call(t: Term): Option[(Term, List[Term])] = t match {
      case a: Term.Apply => Some((a.fun, a.argClause.values))
      case _             => None
    }
    /** Only the helpers that merely forward their own parameters: `raw(text) = Sql.raw(Sql.Text(text))`,
      * `of(text, ps*) = Sql.of(Sql.Text(text), ps*)`, `str(x) = Param.S(Param.S.V(x))`, `strs(xs) = Param.A(xs.map(...))`.
      */
    def helperKind(d: Defn.Def): Option[String] = {
      val names = d.paramClauses.flatMap(_.values).map(_.name.value)
      def isParamRef(t: Term, i: Int): Boolean = names.lift(i).exists(n => unblock(t) match {
        case Term.Name(`n`) => true
        case Term.Repeated(Term.Name(`n`)) => true
        case a: Term.Apply => a.fun match {
          case Term.Select(Term.Name("Sql"), m) if Set("Text", "RawText", "OfText").contains(m.value) =>
            a.argClause.values.size == 1 && isParamRef(a.argClause.values.head, i)
          case _ => false
        }
        case _ => false
      })
      call(body(d)).flatMap { case (f, args) =>
        val sqlCall = f match {
          case Term.Select(Term.Name("Sql"), n) if isSql(n, "raw") && names.size == 1 && args.size == 1 && isParamRef(args.head, 0) =>
            Some("raw")
          case Term.Select(Term.Name("Sql"), n) if isSql(n, "of") && names.size == 2 && args.size == 2 &&
              isParamRef(args.head, 0) && isParamRef(args(1), 1) => Some("of")
          case _ => None
        }
        sqlCall.orElse(paramValue(body(d)).filter(v => names.size == 1 && (isParamRef(v, 0) || (v match {
          case m: Term.Apply => m.fun match {
            case Term.Select(xs, Term.Name("map")) => isParamRef(xs, 0)
            case _ => false
          }
          case _ => false
        }))).map(_ => "param"))
      }
    }
    val helpers: Map[String, (Defn.Def, String)] = doc.tree.collect {
      case d: Defn.Def if helperKind(d).isDefined && d.paramClauses.size == 1 =>
        d.name.symbol.value -> (d, helperKind(d).get)
    }.toMap

    // ---- private String constants used only as SQL text
    def isStringConst(t: Term): Boolean = t match {
      case _: Lit.String                                   => true
      case Term.ApplyInfix.After_4_6_0(l, Term.Name("+"), _, Term.ArgClause(List(r), _)) => isStringConst(l) && isStringConst(r)
      case _                                               => false
    }
    val constCandidates: Map[String, Defn.Val] = doc.tree.collect {
      case v: Defn.Val
          if v.mods.exists(m => m.is[Mod.Private] && m.asInstanceOf[Mod.Private].within.is[Name.Anonymous]) &&
            v.pats.size == 1 && v.pats.head.is[Pat.Var] && isStringConst(v.rhs) =>
        v.pats.head.asInstanceOf[Pat.Var].name.symbol.value -> v
    }.toMap

    /** Inside the text argument of a raw operator (or an `of`/`raw` helper): an s-interpolation's argument, or the whole
      * text, or a part of a `+` concatenation of those.
      */
    def inSqlText(t: Tree): Boolean = t.parent match {
      case Some(i: Term.Interpolate) if i.prefix.value == "s" => inSqlText(i)
      case Some(i: Term.Interpolate) if i.prefix.value == "sql" => false
      case Some(b: Term.Block) => inSqlText(b)
      case Some(Term.ApplyInfix.After_4_6_0(_, Term.Name("+"), _, _)) => inSqlText(t.parent.get)
      case Some(Term.ArgClause(_, _)) => t.parent.flatMap(_.parent) match {
        case Some(a: Term.Apply) => textOperator(a.fun) || (a.fun match {
          case n: Term.Name => n.value == "SqlLiteral" && a.parent.exists(p => p.is[Term.Block] || p.is[Term.Interpolate])
          case _             => false
        })
        case _ => false
      }
      case _ => false
    }
    def textOperator(f: Term): Boolean = f match {
      case Term.Select(Term.Name("Sql"), n) => Set("Text", "RawText", "OfText", "raw").contains(n.value) && isSql(n, n.value)
      case n: Term.Name => helpers.get(sym(n)).exists(h => h._2 == "raw" || h._2 == "of")
      case _ => false
    }
    val constUses: Map[String, List[Term.Name]] = doc.tree.collect {
      case n: Term.Name if constCandidates.contains(sym(n)) && !n.parent.exists(_.is[Pat.Var]) => sym(n) -> n
    }.groupBy(_._1).map { case (k, v) => k -> v.map(_._2) }
    val consts: Set[String] = constCandidates.keySet.filter(k => constUses.getOrElse(k, Nil).forall(inSqlText))

    // ---- strings to segments
    def stripQuotes(src: String): (String, Boolean) =
      if (src.startsWith("\"\"\"")) (src.drop(3).dropRight(3), true) else (src.drop(1).dropRight(1), false)
    def litSeg(l: Lit.String): Text = {
      val (body, triple) = stripQuotes(l.pos.text)
      // a plain literal has no `$` escapes; a triple-quoted one has no backslash escapes either
      val esc = (if (triple) body.replace("\\", "\\\\") else body).replace("$", "$$")
      Text(esc, triple)
    }
    def unblock(t: Term): Term = t match {
      case Term.Block(List(only: Term)) => unblock(only)
      case other                       => other
    }
    def strSegs(e: Term): List[Seg] = unblock(e) match {
      case l: Lit.String => List(litSeg(l))
      case i: Term.Interpolate if i.prefix.value == "s" =>
        val triple = i.pos.text.startsWith("s\"\"\"")
        val parts = i.parts.map(p => Text(p.pos.text, triple))
        parts.head :: i.args.zip(parts.tail).flatMap { case (a, p) => List(argSeg(a), p) }
      case Term.ApplyInfix.After_4_6_0(l, Term.Name("+"), _, Term.ArgClause(List(r), _)) if stringy(l) && stringy(r) =>
        strSegs(l) ++ strSegs(r)
      case n: Term.Name if consts(sym(n)) => List(Code(n.value))
      case other => List(Raw(other.syntax))
    }
    def stringy(t: Term): Boolean = unblock(t) match {
      case _: Lit.String => true
      case i: Term.Interpolate => i.prefix.value == "s"
      case Term.ApplyInfix.After_4_6_0(l, Term.Name("+"), _, Term.ArgClause(List(r), _)) => stringy(l) || stringy(r)
      case _ => false
    }
    def argSeg(a: Tree): Seg = a match {
      case t: Term => unblock(t) match {
        case n: Term.Name if consts(sym(n)) => Code(n.value)
        case other                          => Raw(other.syntax)
      }
      case other => Raw(other.syntax)
    }
    def textSegs(arg: Term): List[Seg] = arg match {
      case a: Term.Apply => a.fun match {
        case Term.Select(Term.Name("Sql"), n) if Set("Text", "RawText", "OfText").contains(n.value) =>
          strSegs(a.argClause.values.head)
        case _ => strSegs(arg)
      }
      case _ => strSegs(arg)
    }

    // ---- parameters
    /** `Param.S(Param.S.V(e))` (or `S(Param.S.V(e))`), `Param.A(xs.map(Param.A.V(_)))`, `Param.A(List(Param.A.V(a), ...))`:
      * the bound value's code.
      */
    def paramValue(t: Term): Option[Term] = t match {
      case a: Term.Apply if isParam(a.fun) && a.argClause.values.size == 1 =>
        val kind = a.fun match {
          case Term.Select(_, n) => n.value
          case n: Term.Name      => n.value
          case _                 => ""
        }
        val inner = a.argClause.values.head
        def vOf(x: Term): Option[Term] = x match {
          case v: Term.Apply if isParam(v.fun) && v.argClause.values.size == 1 && v.fun.syntax.endsWith(".V") =>
            Some(v.argClause.values.head)
          case _ => None
        }
        kind match {
          case "S" | "L" | "I" | "D" => vOf(inner)
          case "A" =>
            inner match {
              case m: Term.Apply => m.fun match {
                case Term.Select(xs, Term.Name("map")) if m.argClause.values.size == 1 =>
                  m.argClause.values.head match {
                    case f: Term.Function if f.paramClause.values.size == 1 =>
                      vOf(f.body).collect {
                        case n: Term.Name if n.value == f.paramClause.values.head.name.value => xs
                        case other => Term.Apply(Term.Select(xs, Term.Name("map")), Term.ArgClause(List(Term.Function(f.paramClause, other))))
                      }
                    case af: Term.AnonymousFunction => vOf(af.body).collect { case _: Term.Placeholder => xs }
                    case v: Term.Select if v.syntax.endsWith(".V") => Some(xs)
                    case _ => None
                  }
                case Term.Name("List") =>
                  val vs = m.argClause.values.map(vOf)
                  if (vs.forall(_.isDefined)) Some(Term.Apply(Term.Name("List"), Term.ArgClause(vs.map(_.get)))) else None
                case _ => None
              }
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
    var usesTextArray = false
    def paramCode(p: Term): String = {
      def helperParam(a: Term.Apply): Option[String] = a.fun match {
        case n: Term.Name if helpers.get(sym(n)).exists(_._2 == "param") && a.argClause.values.size == 1 =>
          if (isArray(helpers(sym(n))._1.body)) usesTextArray = true
          Some(a.argClause.values.head.syntax)
        case _ => None
      }
      p match {
        case a: Term.Apply if helperParam(a).isDefined => helperParam(a).get
        case _ =>
          paramValue(p) match {
            case Some(v) =>
              if (isArray(p)) usesTextArray = true
              v.syntax
            case None => p.syntax // a value of type Param: becomes the codec-typed value by hand
          }
      }
    }
    def isArray(p: Term): Boolean = p.syntax.startsWith("Param.A(") || p.syntax.startsWith("A(")
    def fill(segs: List[Seg], ps: List[Term]): List[Seg] = {
      var rest = ps
      val out = segs.flatMap {
        case Text(t, triple) if t.contains('?') =>
          if (t.contains("??")) bail("?? in the text")
          val pieces = t.split("\\?", -1).toList
          pieces.head.pipe(h => Text(h, triple)) :: pieces.tail.flatMap { piece =>
            rest match {
              case p :: tail => rest = tail; List(Code(paramCode(p)), Text(piece, triple))
              case Nil       => bail("more ? than parameters")
            }
          }
        case other => List(other)
      }
      if (rest.nonEmpty) bail("fewer ? than parameters")
      out
    }
    implicit class Pipe[A](a: A) { def pipe[B](f: A => B): B = f(a) }

    // ---- fragments to segments
    def isPlaceholder(t: Term): Boolean = t.collect { case _: Term.Placeholder => () }.nonEmpty &&
      !t.collect { case _: Term.AnonymousFunction => () }.nonEmpty
    /** A fragment built here, without nesting: its segments. */
    def leaf(t: Term): Option[List[Seg]] = t match {
      case Term.Select(Term.Name("Sql"), n) if n.value == "empty" && isSql(n, "empty") => Some(Nil)
      case a: Term.Apply =>
        val args = a.argClause.values
        a.fun match {
          case Term.Select(Term.Name("Sql"), n) if n.value == "raw" && isSql(n, "raw") && args.size == 1 =>
            Some(textSegs(args.head))
          case Term.Select(Term.Name("Sql"), n) if n.value == "of" && isSql(n, "of") && args.nonEmpty =>
            if (args.tail.exists(_.is[Term.Repeated])) bail("parameters spread from a list")
            Some(fill(textSegs(args.head), args.tail))
          case n: Term.Name if helpers.get(sym(n)).exists(_._2 == "raw") && args.size == 1 => Some(textSegs(args.head))
          case n: Term.Name if helpers.get(sym(n)).exists(_._2 == "of") && args.nonEmpty =>
            if (args.tail.exists(_.is[Term.Repeated])) bail("parameters spread from a list")
            Some(fill(textSegs(args.head), args.tail))
          case Term.Select(q, n) if n.value == "wrap" && isSqlMember(n, "wrap") && args.size == 2 =>
            Some(strSegs(args.head) ++ chain(q) ++ strSegs(args(1)))
          case _ => None
        }
      case Term.Select(q, n) if n.value == "frag" && isSqlMember(n, "frag") => Some(chain(q))
      case i: Term.Interpolate if i.prefix.value == "sql" =>
        val triple = i.pos.text.startsWith("sql\"\"\"")
        val parts = i.parts.map(p => Text(p.pos.text, triple))
        Some(parts.head :: i.args.zip(parts.tail).flatMap { case (a, p) => List(sqlArg(a), p) })
      case _ => None
    }
    def sqlArg(a: Tree): Seg = a match {
      case t: Term => unblock(t) match {
        case ap: Term.Apply if ap.fun.syntax == "SqlLiteral" && ap.argClause.values.size == 1 =>
          ap.argClause.values.head match {
            case n: Term.Name if consts(sym(n)) => Code(n.value)
            case _                              => Code(t.syntax)
          }
        case other => Code(other.syntax)
      }
      case other => Code(other.syntax)
    }
    def isConcat(t: Term): Boolean = t match {
      case i: Term.ApplyInfix => i.op.value == "++" && isSqlMember(i.op, "`++`")
      case _ => false
    }
    /** `a ++ b ++ ...`, every operand a leaf (merged) or nested as it is. */
    def chain(t: Term): List[Seg] = t match {
      case i: Term.ApplyInfix if isConcat(i) =>
        val r = i.argClause.values.head
        chain(i.lhs) ++ (if (stringy(r)) strSegs(r) else chain(r))
      case _ =>
        leaf(t).getOrElse {
          if (isPlaceholder(t)) bail("placeholder operand")
          List(Keep(t))
        }
    }
    def convertible(t: Term): Boolean = isConcat(t) || leaf0(t)
    def leaf0(t: Term): Boolean = t match {
      case _: Term.Interpolate => false
      case _ => scala.util.Try(leaf(t)).toOption.flatten.isDefined || scala.util.Try(leaf(t)).isFailure
    }

    // ---- emission
    def merge(segs: List[Seg]): List[Seg] = segs.foldRight(List.empty[Seg]) {
      case (Text(a, ta), Text(b, tb) :: rest) => Text(a + b, ta || tb) :: rest
      case (s, acc) => s :: acc
    }
    var usesSql = false
    var usesLiteral = false
    /** The patch turning `root` into one `sql"..."`; the kept subtrees stay where they are. */
    def emit(root: Term, segs0: List[Seg]): Patch = {
      val segs = merge(segs0.filter { case Text("", _) => false; case _ => true })
      val triple = segs.exists { case Text(_, t) => t; case _ => false }
      val q = if (triple) "\"\"\"" else "\""
      usesSql = true
      segs match {
        case List(Keep(t)) => // a lone nested fragment is itself
          Patch.removeTokens(root.tokens.filter(tok => tok.start < t.pos.start || tok.end > t.pos.end))
        case _ =>
          val ident = "[A-Za-z_][A-Za-z0-9_]*".r
          /** `$name` where the text after it cannot continue the name, else `${code}`. */
          def plain(code: String, i: Int): Boolean = ident.pattern.matcher(code).matches() && (segs.lift(i + 1) match {
            case Some(Text(x, _)) => !x.headOption.exists(c => c.isLetterOrDigit || c == '_')
            case _                => true
          })
          def piece(s: Seg, i: Int): String = s match {
            case Text(x, _) => x
            case Code(c)    => if (plain(c, i)) "$" + c else "${" + c + "}"
            case Raw(c)     => usesLiteral = true; "${SqlLiteral(" + c + ")}"
            case Keep(_)    => sys.error("kept")
          }
          // split into gaps around kept subtrees
          val keeps = segs.collect { case Keep(t) => t }
          val keepPlain: List[Boolean] = segs.zipWithIndex.collect { case (Keep(t), i) => plain(t.syntax, i) }
          val gaps0: List[String] = {
            val b = List.newBuilder[String]; val cur = new StringBuilder
            segs.zipWithIndex.foreach {
              case (Keep(_), _) => b += cur.toString; cur.clear()
              case (s, i)       => cur ++= piece(s, i)
            }
            b += cur.toString; b.result()
          }
          // a quote right before the closing `"""` would close it early: escaped, as `sql` reads escapes like `s`
          val gaps =
            if (!triple) gaps0
            else {
              val last = gaps0.last
              val body = last.reverse.dropWhile(_ == '"').reverse
              gaps0.init :+ (body + "\\\"" * (last.length - body.length))
            }
          if (keeps.isEmpty) Patch.replaceTree(root, "sql" + q + gaps.head + q)
          else {
            val bounds = keeps.map(k => (k.pos.start, k.pos.end))
            def gapTokens(from: Int, to: Int) = root.tokens.filter(tok => tok.start >= from && tok.end <= to).toList
            val starts = root.pos.start :: bounds.map(_._2)
            val ends = bounds.map(_._1) :+ root.pos.end
            val texts = gaps.zipWithIndex.map { case (g, i) =>
              (if (i == 0) "sql" + q else if (keepPlain(i - 1)) "" else "}") + g +
                (if (i == gaps.size - 1) q else if (keepPlain(i)) "$" else "${")
            }
            starts.zip(ends).zip(texts).zipWithIndex.map { case (((from, to), text), i) =>
              val ts = gapTokens(from, to)
              if (ts.nonEmpty) Patch.removeTokens(ts) + Patch.addLeft(ts.head, text)
              else if (i == 0) Patch.addLeft(keeps.head.tokens.head, text)
              else if (i == gaps.size - 1) Patch.addRight(keeps.last.tokens.last, text)
              else bail("adjacent nested fragments")
            }.asPatch
          }
      }
    }

    def importPatches(text: String): Patch = {
      val magnumImports = doc.tree.collect { case i: Importer if i.ref.syntax == "com.augustnagro.magnum" => i }
      val wildcard = magnumImports.exists(_.importees.exists(_.is[Importee.Wildcard]))
      val named = magnumImports.flatMap(_.importees.collect { case Importee.Name(n) => n.value }).toSet
      val needed = List(
        "Frag" -> (text.contains("Frag")),
        "sql" -> usesSql,
        "SqlLiteral" -> usesLiteral
      ).collect { case (n, true) if !wildcard && !named(n) => n }
      val magnum = if (needed.isEmpty) Patch.empty else {
        val sel = if (needed.size == 1) needed.head else needed.mkString("{", ", ", "}")
        Patch.addGlobalImport(s"com.augustnagro.magnum.$sel".parse[Importer].get)
      }
      val dbGiven = doc.tree.collect { case i: Importer if i.ref.syntax == "com.histmersion.util.database" && i.importees.exists(_.is[Importee.Given]) => i }.nonEmpty
      val inDbPkg = doc.tree.collect { case p: Pkg => p.ref.syntax }.exists(r => r == "util.database" || r == "com.histmersion.util.database")
      val arrays = if (usesTextArray && !dbGiven && !inDbPkg) Patch.addGlobalImport(importer"com.histmersion.util.database.given") else Patch.empty
      magnum + arrays
    }

    lazy val patch: Patch = {
      var fragType = false
      def go(t: Tree): Patch = t match {
        case term: Term if convertible(term) =>
          try {
            val segs = if (isConcat(term)) chain(term) else leaf(term).get
            val keeps = segs.collect { case Keep(k) => k }
            emit(term, segs) + keeps.map(go).asPatch
          } catch {
            case b: Bail => note(term, b.reason); t.children.map(go).asPatch
          }
        case Term.Select(q, n) if n.value == "text" && isSqlMember(n, "text") =>
          val inner = t.parent match {
            case Some(Term.Select(_, v)) if v.value == "value" => Patch.removeTokens(t.parent.get.tokens.filter(_.start >= t.pos.end))
            case _ => Patch.empty
          }
          Patch.replaceTree(n, "sqlString") + inner + go(q)
        case a: Term.Apply if paramValue(a).isDefined && helpers.forall(_._2._1.body ne a) =>
          // a standalone parameter (a keyset cursor, a bailed fragment's): the value itself
          val v = paramValue(a).get
          if (isArray(a)) usesTextArray = true
          if (v.pos == Position.None) Patch.replaceTree(a, v.syntax)
          else Patch.removeTokens(a.tokens.filter(tok => tok.start < v.pos.start || tok.end > v.pos.end)) + go(v)
        case ap: Term.Apply if ap.fun.syntax == "SqlLiteral" && ap.argClause.values.size == 1 && (ap.argClause.values.head match {
              case n: Term.Name => consts(sym(n)); case _ => false
            }) =>
          Patch.replaceTree(ap, ap.argClause.values.head.syntax)
        case n: Type.Name if n.value == "Sql" && sym(n) == SqlCls =>
          fragType = true; Patch.replaceTree(n, "Frag")
        case d: Defn.Def if helpers.contains(d.name.symbol.value) =>
          Patch.removeTokens(d.tokens) + Patch.removeTokens(trailingNewline(d))
        case v: Defn.Val if v.pats.size == 1 && v.pats.head.is[Pat.Var] && consts(v.pats.head.asInstanceOf[Pat.Var].name.symbol.value) =>
          usesSql = true
          val segs = merge(strSegs(v.rhs))
          val triple = segs.exists { case Text(_, tr) => tr; case _ => false }
          val q = if (triple) "\"\"\"" else "\""
          val text = segs.map { case Text(x, _) => x; case Code(c) => "${" + c + "}"; case other => bail(s"$other") }.mkString
          fragType = fragType || v.decltpe.nonEmpty
          v.decltpe.map(tp => Patch.replaceTree(tp, "Frag")).asPatch + Patch.replaceTree(v.rhs, "sql" + q + text + q)
        case i: Importee.Name if (i.name.value == "Sql" || i.name.value == "Param") && i.parent.exists {
              case imp: Importer => imp.ref.syntax.endsWith("util.database")
              case _ => false
            } =>
          Patch.removeImportee(i)
        case other => other.children.map(go).asPatch
      }
      val body = go(doc.tree)
      val all = body + importPatches(if (fragType) "Frag" else "")
      write(rows.result())
      all
    }
    /** The indentation before a removed definition and the rest of its line, up to and including the line break. */
    def trailingNewline(d: Tree): List[Token] = {
      val all = doc.tree.tokens
      val before = all.takeWhile(_.end <= d.pos.start).reverse.takeWhile(t => t.is[Token.Whitespace] && !t.is[Token.LF]).toList
      val after = all.dropWhile(_.start < d.pos.end).takeWhile(t => t.is[Token.Whitespace])
      before ++ after.takeWhile(t => !t.is[Token.LF]).toList ++ after.find(_.is[Token.LF]).toList
    }
  }
}
