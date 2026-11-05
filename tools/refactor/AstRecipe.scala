import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object AstRecipe {
  case class Edit(path: String = "", before: String = "", after: String = "", count: Int = 1, kind: String = "stat")
  object Edit {
    implicit val surface: generic.Surface[Edit] = generic.deriveSurface[Edit]
    implicit val decoder: ConfDecoder[Edit] = generic.deriveDecoder(Edit()).noTypos
  }
  case class Policy(manifest: String = "", report: String = "", edits: List[Edit] = Nil, creates: Map[String,String] = Map.empty, appendStats: Map[String,String] = Map.empty, relocatedPreview: String = "")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class AstRecipe(val policy: AstRecipe.Policy) extends SyntacticRule("AstRecipe") {
  def this() = this(AstRecipe.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("AstRecipe")(AstRecipe.Policy()).map(new AstRecipe(_))
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def read(p: String) = Files.readString(Paths.get(p))
    def fragment(s: String): String = s.parse[Source] match {
      case Parsed.Success(t) => t.structure
      case _ => s.parse[Stat].get.structure
    }
    def rows(p: String) = Files.readAllLines(Paths.get(p)).asScala.drop(1).map(_.split("\t",-1)).toList
    val initial = rows(policy.manifest).map(r => r(0) -> r(1)).toMap
    val frozen = if(policy.relocatedPreview.isEmpty) initial else {
      require(policy.manifest == policy.relocatedPreview + "/sources.tsv" && Files.exists(Paths.get(policy.relocatedPreview,"applied.txt")))
      val changes = rows(policy.relocatedPreview + "/plan.tsv").map(r => r(0) -> r(2)).toMap
      val moves = rows(policy.relocatedPreview + "/moves.tsv").map(r => r(0) -> r(1)).toMap
      val mapped = initial.toList.map { case(p,h) => moves.getOrElse(p,p) -> changes.getOrElse(p,h) }
      require(mapped.map(_._1).distinct.size == initial.size)
      mapped.toMap
    }
    frozen.foreach { case(p,h) => require(hash(read(p)) == h, "Stale source: " + p) }
    val out = Paths.get(policy.report)
    require(!Files.exists(out) && (policy.edits.nonEmpty || policy.creates.nonEmpty || policy.appendStats.nonEmpty))
    require(policy.appendStats.keySet.subsetOf(policy.edits.map(_.path).toSet), "Append requires a reviewed edit in the same file")
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    val changed = policy.edits.groupBy(_.path).toList.sortBy(_._1).map { case(path, recipes) =>
      require(frozen.contains(path))
      val source = read(path)
      val tree = source.parse[Source].get
      val patches = recipes.flatMap { recipe =>
        require(Set("stat","type")(recipe.kind))
        val pattern: Tree = if(recipe.kind == "type") recipe.before.parse[Type].get else recipe.before.parse[Stat].get
        val replacementAst = if(recipe.kind == "type") recipe.after.parse[Type].get.structure else fragment(recipe.after)
        val matches = tree.collect { case t if t.structure == pattern.structure => t }
        require(matches.size == recipe.count && recipe.count > 0, "AST count mismatch: " + path + " " + recipe.before)
        matches.map { t =>
          val prefix = source.substring(source.lastIndexOf('\n', t.pos.start - 1) + 1, t.pos.start)
          val indent = prefix.takeWhile(_ == ' ')
          val replacement = recipe.after.linesIterator.zipWithIndex.map { case(line,i) => if(i == 0) line else indent + line }.mkString("\n")
          val end = if(recipe.after.isEmpty) tree.tokens.find(tok => tok.pos.start == t.pos.end && tok.is[Token.LF]).map(_.pos.end).getOrElse(t.pos.end) else t.pos.end
          val start = if(recipe.after.isEmpty && prefix.forall(_ == ' ')) t.pos.start - prefix.length else t.pos.start
          evidence += List(path, (t.pos.startLine + 1).toString, t.structure, replacementAst)
          (start, end, replacement)
        }
      } ++ policy.appendStats.get(path).toList.map { snippet =>
        val appended = snippet.parse[Source].get
        require(appended.stats.nonEmpty)
        evidence += List(path, (tree.pos.endLine + 1).toString, "append-after-source", appended.structure)
        (tree.pos.end, tree.pos.end, "\n\n" + snippet + "\n")
      }
      val ordered = patches.sortBy(_._1)
      require(ordered.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Overlapping AST patches")
      val next = ordered.reverse.foldLeft(source) { case(s,(a,b,n)) => s.patch(a,n,b-a) }
      next.parse[Source].get
      (path, source, next)
    }
    val created = policy.creates.toList.sortBy(_._1).map { case(p,s) =>
      require(!Files.exists(Paths.get(p)) && !policy.edits.exists(_.path == p)); s.parse[Source].get; (p,"",s)
    }
    Files.createDirectories(out)
    (changed ++ created).foreach { case(p,b,n) =>
      val dest = out.resolve("sources").resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,n)
      if(b.nonEmpty) { val old = out.resolve("before").resolve(p); Files.createDirectories(old.getParent); Files.writeString(old,b) }
    }
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(out.resolve("shapes.tsv"),("path\tline\tbefore-ast\tafter-ast" :: evidence.toList.map(_.map(escape).mkString("\t"))).asJava)
    Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: (changed ++ created).map { case(p,b,n) => List(p,if(b.isEmpty) "new" else hash(b),hash(n)).mkString("\t") }).asJava)
    Files.writeString(out.resolve("status.txt"),"PREVIEW_ONLY\nfiles=" + (changed.size + created.size) + "\nExact AST recipes scoped to frozen source hashes; not a semantic generalization or repair proof.\n")
    println("Preview: " + (changed.size + created.size) + " files, " + evidence.size + " matched ASTs")
  }
}
