import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder,Configured,generic}
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

object CopyToLens {
  case class Policy(mode: String = "inspect", declarations: String = "", manifest: String = "", report: String = "target/copy-to-lens",
    modules: List[String] = List("api","core","db","files","server","cli","web","plugin-host"), tests: Boolean = false)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def decode(s: String): String = {
    val out = new StringBuilder; var i = 0
    while(i < s.length) {
      if(s(i) == '\\' && i+1 < s.length) {
        i += 1; out.append(s(i) match { case 'n' => '\n'; case 't' => '\t'; case 'r' => '\r'; case c => c })
      } else out.append(s(i))
      i += 1
    }
    out.toString
  }
}
class CopyToLens(val policy: CopyToLens.Policy) extends SemanticRule("CopyToLens") {
  def this() = this(CopyToLens.Policy())
  import CopyToLens._
  private implicit val dialect: Dialect = dialects.Scala3
  private val rows = new ConcurrentLinkedQueue[List[String]]()
  private lazy val indexed: Map[String,Defn.Class] = if(policy.declarations.isEmpty) Map.empty else {
    val stream = Files.lines(Paths.get(policy.declarations),UTF_8)
    try stream.iterator.asScala.drop(1).flatMap { line =>
      val r = line.split("\t",-1)
      if(r(7) != "case-class") None else decode(r(13)).parse[Stat].toOption.collect { case c: Defn.Class => r(4) -> c }
    }.toMap finally stream.close()
  }
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("CopyToLens")(Policy()).map { p =>
    require(Set("inspect","apply","check")(p.mode),"Unknown CopyToLens mode")
    if(p.declarations.nonEmpty) {
      require(p.manifest.nonEmpty,"Class index requires a source manifest")
      Files.readAllLines(Paths.get(p.manifest),UTF_8).asScala.drop(1).foreach { line =>
        val r = line.split("\t",-1)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get(r(0)))).map(b => f"${b & 255}%02x").mkString
        require(hash == r(1),"Stale class index source: " + r(0))
      }
    }
    new CopyToLens(p)
  }
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def name(t: Term): Option[Name] = t match {
    case s: Term.Select => Some(s.name)
    case n: Term.Name => Some(n)
    case a: Term.ApplyType => name(a.fun)
    case a: Term.Apply => name(a.fun)
    case _ => None
  }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p,_) => p.toString; case Input.VirtualFile(p,_) => p }
    val module = path.split("/modules/",2).last.split("/src/",2).head
    val local = doc.tree.collect { case c: Defn.Class if c.mods.exists(_.is[Mod.Case]) => c.name.symbol.value -> c }.toMap
    var changed = false
    val patches = doc.tree.collect { case a: Term.Apply if name(a.fun).exists(n => n.value == "copy" || n.symbol.displayName == "copy") =>
      val n = name(a.fun).get
      val receiverOwner = a.fun match {
        case Term.Select(call: Term.Apply,_) => call.fun.symbol.info.toList.flatMap(_.signature match {
          case MethodSignature(_,_,TypeRef(_,s,_)) => List(s.value)
          case _ => Nil
        }).headOption
        case _ => None
      }
      val cls = local.get(n.symbol.owner.value).orElse(indexed.get(n.symbol.owner.value)).orElse(
        if(n.symbol.isNone) receiverOwner.flatMap(s => local.get(s).orElse(indexed.get(s))) else None)
      val args = a.argClause.values
      val assignment = args match { case List(v: Term.Assign) if v.lhs.is[Term.Name] => Some(v); case _ => None }
      val inFold = parents(a).exists {
        case call: Term.Apply => name(call.fun).exists(n => Set("fold","foldLeft","foldRight")(n.value))
        case d: Defn.Def => d.mods.exists { case m: Mod.Annot => m.init.tpe.syntax.endsWith("tailrec"); case _ => false }
        case _ => false
      }
      val reason = if(cls.isEmpty) "unresolved-or-non-case-class-owner"
        else if(!policy.tests && path.contains("/src/test/")) "test-deferred"
        else if(policy.modules.nonEmpty && !policy.modules.contains(module)) "module-optics-dependency-review"
        else if(cls.get.tparams.nonEmpty) "generic-copy-type-change-review"
        else if(cls.get.ctor.paramss.size != 1) "multiple-constructor-clauses"
        else if(cls.get.templ.stats.exists { case d: Defn.Def => Set("copy","modify")(d.name.value); case _ => false }) "custom-copy-or-modify"
        else if(inFold) "accumulator-algorithm-review"
        else if(a.fun.is[Term.ApplyType]) "explicit-type-arguments"
        else if(assignment.isEmpty) "multi-field-positional-or-empty-copy"
        else if(!cls.get.ctor.paramss.flatten.exists(p => p.name.value == assignment.get.lhs.syntax && !p.mods.exists(m => m.is[Mod.Private] || m.is[Mod.Protected]))) "field-access-review"
        else if(assignment.get.tokens.takeWhile(_.pos.start < assignment.get.rhs.pos.start).exists(_.is[Token.Comment])) "argument-comment-review"
        else "eligible"
      rows.add(List(path,(a.pos.startLine+1).toString,a.pos.start.toString,n.symbol.value,reason,a.pos.text,a.structure))
      if(reason == "eligible" && policy.mode == "apply") {
        changed = true
        val v = assignment.get
        val prefix = v.tokens.takeWhile(_.pos.start < v.rhs.pos.start)
        val replacement = if(a.fun.is[Term.Name]) "this.modify" else "modify"
        (Patch.replaceTree(n,replacement) + Patch.removeTokens(prefix) + Patch.addLeft(v.rhs,"_." + v.lhs.syntax + ").setTo(")).atomic
      } else if(reason == "eligible" && policy.mode == "check") Patch.lint(Diagnostic("remaining",reason,a.pos))
      else Patch.empty
    }.asPatch
    val imported = doc.tree.collect { case i: Importer if Set("hearth.kindlings.optics.syntax","hearth.kindlings.optics")(i.ref.syntax) && i.importees.exists(_.is[Importee.Wildcard]) => i }.nonEmpty
    if(changed && !imported) {
      val anchor = doc.tree.collect { case p: Pkg if p.body.stats.nonEmpty => p.body.stats.head }.headOption.getOrElse(doc.tree.asInstanceOf[Source].stats.head)
      patches + Patch.addLeft(anchor,"import hearth.kindlings.optics.syntax.*\n\n" + (" " * anchor.pos.startColumn))
    } else patches
  }
  override def afterComplete(): Unit = {
    val out = Paths.get(policy.report); Files.createDirectories(out)
    def esc(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    val all = rows.asScala.toList.sortBy(r => (r.head,r(2).toInt))
    Files.write(out.resolve("findings.tsv"),("path\tline\toffset\tsymbol\tstatus\tsource\tast" :: all.map(_.map(esc).mkString("\t"))).asJava,UTF_8)
    Files.write(out.resolve("counts.tsv"),("status\tcount" :: all.groupBy(_(4)).toList.sortBy(_._1).map { case(s,rs) => s + "\t" + rs.size }).asJava,UTF_8)
  }
}
