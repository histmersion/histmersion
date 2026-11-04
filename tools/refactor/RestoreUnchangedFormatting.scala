import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class RestoreUnchangedFormatting extends SyntacticRule("RestoreUnchangedFormatting") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val restored = new java.util.concurrent.ConcurrentLinkedQueue[String]()
  private def normalized(tree: Tree): String = {
    object normalize extends Transformer {
      override def apply(tree: Tree): Tree = tree match {
        case c: Defn.Class => super.apply(c.copy(mods = c.mods.sortBy(_.structure)))
        case Term.Block(List(n: Term.Name)) => n
        case other => super.apply(other)
      }
    }
    normalize.transform(tree).structure
  }
  private lazy val changed = {
    val p = new ProcessBuilder("git","diff","--name-only","--","modules").start()
    val text = new String(p.getInputStream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
    require(p.waitFor() == 0)
    text.linesIterator.toSet
  }
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match { case Input.File(p,_) => Paths.get(p.toString); case Input.VirtualFile(p,_) => Paths.get(p) }
    val relative = Paths.get("").toAbsolutePath.relativize(path.toAbsolutePath).toString
    if(!changed(relative)) Patch.empty else {
      val p = new ProcessBuilder("git","show","HEAD:" + relative).start()
      val original = new String(p.getInputStream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)
      require(p.waitFor() == 0,"Cannot read original: " + relative)
      val same = original.parse[Source].toOption.exists(t => normalized(t) == normalized(doc.tree))
      if(same && original != doc.input.text) { restored.add(relative); Patch.replaceTree(doc.tree,original) }
      else Patch.empty
    }
  }
  override def afterComplete(): Unit = {
    if(!restored.isEmpty) Files.write(Paths.get("target/copy-formatting-restored.txt"),restored.asScala.toList.sorted.asJava)
    val applied = Files.readAllLines(Paths.get("refactor-audit/copy-migration/applied/findings.tsv")).asScala.drop(1).map(_.split("\t",-1)).filter(_(4) == "eligible").map(r => Paths.get("").toAbsolutePath.relativize(Paths.get(r(0))).toString).toSet
    println("Changes outside rewrite batch: " + (changed -- applied).toList.sorted.mkString(", "))
  }
}
