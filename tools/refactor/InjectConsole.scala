import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object InjectConsole {
  case class Policy(mode: String = "inspect", plan: String = "refactor-audit/console-plan.tsv")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class InjectConsole(val policy: InjectConsole.Policy) extends SemanticRule("InjectConsole") {
  def this() = this(InjectConsole.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("InjectConsole")(InjectConsole.Policy()).map(new InjectConsole(_))
  private val nodes = new ConcurrentLinkedQueue[(String, String, Int, Set[String], Boolean)]()
  private val planRows = if (policy.mode == "apply") Files.readAllLines(Paths.get(policy.plan), UTF_8).asScala.drop(1).map(_.split("\t").toList).toList else Nil
  require(planRows.forall(_.size == 4), "Regenerate the Console inventory before applying: source hashes are required")
  private val selected = planRows.map(_.head).toSet
  private val hashes = new java.util.concurrent.ConcurrentHashMap[String, String]()
  private val helpers = Set("write", "writeLine", "errorWrite", "errorLine", "errorFlush", "processErrorWrite", "processErrorLine", "processErrorFlush")
  private def method(n: Name)(implicit doc: SemanticDocument): Option[String] = {
    val s = n.symbol.value
    if (s.startsWith("com/histmersion/cli/Cli.") && helpers(n.value)) Some(n.value.replace("processError", "error"))
    else if (SymbolMatcher.normalized("scala.Predef.println").matches(n)) Some("writeLine")
    else if (SymbolMatcher.normalized("scala.Predef.print").matches(n)) Some("write")
    else if (SymbolMatcher.normalized("scala.io.StdIn.readLine").matches(n)) Some("readLine")
    else None
  }
  private def raw(t: Tree)(implicit doc: SemanticDocument): Option[(Tree, String)] = t match {
    case s @ Term.Select(q, n) if Set("print", "println", "flush")(n.value) && Set("scala/Console.err.", "scala/Console.err().", "java/lang/System#err.")(q.symbol.value) =>
      Some((s, Map("print" -> "errorWrite", "println" -> "errorLine", "flush" -> "errorFlush")(n.value)))
    case _ => None
  }
  private def member(t: Tree): Boolean = t.parent.exists(p => p.is[Template] || p.productPrefix == "Template.Body")
  private def key(s: String, path: String): String = if (s.startsWith("local")) path + "::" + s else s
  private def owner(t: Tree): Option[Tree] = t.parent.flatMap {
    case d: Defn.Def => Some(d)
    case v: Defn.Val if member(v) => Some(v)
    case p => owner(p)
  }
  private def name(t: Tree): Name = t match {
    case d: Defn.Def => d.name
    case v: Defn.Val => v.pats.head.asInstanceOf[Pat.Var].name
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case x => x.toString }
    if (!path.contains("/cli/src/main/scala/")) return Patch.empty
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(doc.input.text.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
    hashes.put(path, hash)
    planRows.filter(_(1) == path).foreach(r => require(r(3) == hash, s"Stale Console inventory for $path; recompile and regenerate before applying"))
    val declarations = doc.tree.collect {
      case d: Defn.Def => d: Tree
      case v: Defn.Val if member(v) && v.pats.size == 1 && v.pats.head.is[Pat.Var] => v: Tree
    }
    val ops = doc.tree.collect {
      case n: Term.Name if method(n).nonEmpty && !n.parent.exists { case d: Defn.Def => d.name eq n; case _ => false } =>
        (n.parent.collect { case s: Term.Select if s.name eq n => s: Tree }.getOrElse(n), method(n).get)
      case t if raw(t).nonEmpty => raw(t).get
    }
    declarations.foreach { d =>
      val refs = d.collect { case n: Term.Name if owner(n).contains(d) => key(n.symbol.value, path) }.toSet
      nodes.add((key(name(d).symbol.value, path), path, d.pos.startLine + 1, refs, ops.exists(x => owner(x._1).contains(d))))
    }
    if (policy.mode != "apply") return Patch.empty
    val patches = scala.collection.mutable.ListBuffer.empty[Patch]
    declarations.foreach { d =>
      val n = name(d)
      val alreadyInjected = d match {
        case f: Defn.Def => f.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).exists(_.name.value == "console") ||
          (f.name.value == "histmersionCli" && f.body.syntax.contains("Console.system"))
        case _ => false
      }
      if (n.symbol.value.startsWith("com/histmersion/cli/Cli.") && helpers(n.value)) patches += Patch.removeTokens(d.tokens)
      else if (selected(key(n.symbol.value, path)) && !alreadyInjected) {
        if (n.value == "histmersionCli") {
          val body = d.asInstanceOf[Defn.Def].body
          patches += Patch.replaceTree(body, "{\n  given com.histmersion.util.console.Console = com.histmersion.util.console.Console.system\n  " + body.pos.text + "\n}")
        } else {
          val anchor = d match {
            case f: Defn.Def => f.decltpe.map(t => f.tokens.takeWhile(_.pos.start < t.pos.start).reverse.find(_.is[Token.Colon]).get).getOrElse(f.tokens.find(t => t.is[Token.Equals] && t.pos.start < f.body.pos.start).get)
            case v: Defn.Val =>
              patches += Patch.replaceToken(v.tokens.find(_.is[Token.KwVal]).get, "def")
              v.mods.collect { case m: Mod.Lazy => patches += Patch.removeTokens(m.tokens) }
              v.decltpe.map(t => v.tokens.takeWhile(_.pos.start < t.pos.start).reverse.find(_.is[Token.Colon]).get).getOrElse(v.tokens.find(t => t.is[Token.Equals] && t.pos.start < v.rhs.pos.start).get)
          }
          patches += Patch.addLeft(anchor, "(using console: com.histmersion.util.console.Console) ")
        }
      }
    }
    ops.foreach { case (t, op) =>
      val inRemoved = owner(t).exists(d => name(d).symbol.value.startsWith("com/histmersion/cli/Cli.") && helpers(name(d).value))
      if (!inRemoved) patches += Patch.replaceTree(t, "console." + op)
    }
    patches.toList.asPatch
  }
  override def afterComplete(): Unit = if (policy.mode == "inspect") {
    val all = nodes.asScala.toList
    var needed = all.filter(_._5).map(_._1).toSet
    var previous = Set.empty[String]
    while (previous != needed) { previous = needed; needed ++= all.filter(n => n._4.exists(needed)).map(_._1) }
    val rows = all.filter(n => needed(n._1)).sortBy(n => (n._2, n._3)).map(n => List(n._1, n._2, n._3.toString, hashes.get(n._2)).mkString("\t"))
    Files.write(Paths.get(policy.plan), ("symbol\tpath\tline\tsource-sha256" :: rows).asJava, UTF_8)
  }
}
