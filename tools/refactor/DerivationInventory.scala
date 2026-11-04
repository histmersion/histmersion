import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue

class DerivationInventory extends SemanticRule("DerivationInventory") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val rows = new ConcurrentLinkedQueue[List[String]]()
  private val references = new ConcurrentLinkedQueue[List[String]]()
  private val files = new ConcurrentLinkedQueue[String]()
  private def names(t: Tree)(implicit d: SemanticDocument) = t.collect { case n: Name if !n.symbol.isNone => n.pos.text + "=" + n.symbol.value }.mkString("; ")
  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p }
    files.add(path)
    doc.tree.collect {
      case g: Defn.GivenAlias =>
        val kind = if (g.body.syntax.startsWith("summon[KindlingsSchema[") && g.body.syntax.endsWith("].schema")) "schema-bridge" else "other-given"
        rows.add(List(kind, path, (g.pos.startLine + 1).toString, g.name.symbol.value, g.pos.text, g.structure, names(g)))
      case n: Name if n.value == "derived" && !n.symbol.isNone =>
        val tree = n.parent.getOrElse(n)
        rows.add(List("derived-call", path, (n.pos.startLine + 1).toString, n.symbol.value, tree.pos.text, tree.structure, names(tree)))
      case c: Defn.Class if c.templ.syntax.contains("KindlingsSchema") =>
        rows.add(List("class", path, (c.pos.startLine + 1).toString, c.name.symbol.value, c.pos.text, c.structure, names(c.templ)))
      case _ => ()
    }
    doc.tree.collect { case n: Name if !n.symbol.isNone =>
      if (!n.parent.exists { case g: Defn.GivenAlias => g.name eq n; case _ => false })
        references.add(List(n.symbol.value, path, (n.pos.startLine + 1).toString, n.parent.getOrElse(n).pos.text, n.parent.getOrElse(n).structure))
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    val stream = Files.walk(Paths.get("modules").toAbsolutePath.normalize)
    val expected = try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") && (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toSet finally stream.close()
    require(files.asScala.toSet == expected, "Incomplete source coverage")
    val out = Paths.get("refactor-audit/derivations")
    Files.createDirectories(out)
    def esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    def write(name: String, header: String, rs: List[List[String]]) = Files.write(out.resolve(name), (header :: rs.map(_.map(esc).mkString("\t"))).asJava, UTF_8)
    val all = rows.asScala.toList.sortBy(r => (r(1),r(2).toInt))
    write("inventory.tsv", "kind\tpath\tline\tsymbol\tsource\tast\tresolved-names", all)
    val givens = all.filter(r => r.head.endsWith("given") || r.head == "schema-bridge").map(_(3)).toSet
    write("references.tsv", "symbol\tpath\tline\tsource\tast", references.asScala.toList.filter(r => givens(r.head)).distinct.sortBy(r => (r(1),r(2).toInt)))
    val main = all.filter(_(1).contains("/src/main/"))
    Files.write(out.resolve("summary.txt"), (List(s"files=${files.size}") ++ main.groupBy(_.head).toList.sortBy(_._1).map { case (k,v) => s"$k=${v.size}" } ++ main.filter(_.head == "derived-call").groupBy(_(3)).toList.sortBy(-_._2.size).map { case (s,rs) => s"${rs.size}\t$s" }).asJava, UTF_8)
  }
}
