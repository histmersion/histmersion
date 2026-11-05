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
  case class Policy(manifest: String = "", report: String = "", edits: List[Edit] = Nil, creates: Map[String,String] = Map.empty, appendStats: Map[String,String] = Map.empty, relocatedPreview: String = "", appliedPlans: List[String] = Nil,
    definitionRoots: List[String] = Nil, reuse: Map[String,String] = Map.empty, names: Map[String,String] = Map.empty)
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
    val initial = policy.appliedPlans.foldLeft(rows(policy.manifest).map(r => r(0) -> r(1)).toMap) { (before, plan) =>
      val changes = rows(plan)
      require(changes.forall(_.length == 3) && changes.map(_(0)).distinct.size == changes.size)
      changes.foreach(r => require(before.get(r(0)).contains(r(1)), "Patch chain does not match manifest: " + r(0)))
      before ++ changes.map(r => r(0) -> r(2))
    }
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
    require(!Files.exists(out) && (policy.edits.nonEmpty || policy.creates.nonEmpty || policy.appendStats.nonEmpty || policy.definitionRoots.nonEmpty))
    require(policy.appendStats.keySet.subsetOf(policy.edits.map(_.path).toSet), "Append requires a reviewed edit in the same file")
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    val definitionPaths = frozen.keys.filter(p => policy.definitionRoots.exists(p.startsWith) && p.contains("/src/main/")).toSet
    val selected = policy.edits.groupBy(_.path) ++ definitionPaths.filterNot(policy.edits.map(_.path).contains).map(_ -> List.empty[AstRecipe.Edit])
    val changed = selected.toList.sortBy(_._1).flatMap { case(path, recipes) =>
      require(frozen.contains(path))
      val source = read(path)
      val tree = source.parse[Source].get
      val definitionPatches = if(definitionPaths(path)) ModelDefinitions.patches(tree, source, path, policy.reuse, policy.names, evidence) else Nil
      val patches = definitionPatches ++ recipes.flatMap { recipe =>
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
      if(source == next) Nil else List((path, source, next))
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
    Files.writeString(out.resolve("status.txt"),"PREVIEW_ONLY\nfiles=" + (changed.size + created.size) + "\nAST transformations scoped to frozen source hashes; definition conversion is not compilation or behavioral repair proof.\n")
    println("Preview: " + (changed.size + created.size) + " files, " + evidence.size + " matched ASTs")
    if(policy.definitionRoots.nonEmpty) {
      val converted = evidence.toList.filter(r => r(2).contains("#") && !r(2).startsWith("UNRESOLVED"))
      val summary = s"Converted primitive leaves: ${converted.size}; models: ${converted.map(_(2).takeWhile(_ != '#')).distinct.size}; blocked primitive leaves: ${evidence.count(_(2).startsWith("UNRESOLVED"))}\n"
      Files.writeString(out.resolve("status.txt"),summary,java.nio.file.StandardOpenOption.APPEND)
      println(summary)
    }
  }
}

object ModelDefinitions {
  def patches(tree: Source, source: String, path: String, reuse: Map[String,String], names: Map[String,String],
      evidence: scala.collection.mutable.ListBuffer[List[String]]): List[(Int,Int,String)] = {
    val primitive = Set("String", "Boolean", "Byte", "Short", "Int", "Long", "Float", "Double", "Char")
    val result = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def indent(t: Tree): String = source.substring(source.lastIndexOf('\n',t.pos.start - 1) + 1,t.pos.start).takeWhile(_ == ' ')
    def upper(s: String): String = s.head.toUpper + s.tail
    def base(t: Type): Option[String] = t match {
      case Type.Name(n) if primitive(n) => Some(n)
      case Type.Select(Term.Name("scala"),Type.Name(n)) if primitive(n) => Some(n)
      case _ => None
    }
    def leaves(t: Type, suffix: String = ""): List[(Type,String,String)] = base(t).map(b => List((t,b,suffix))).getOrElse(t match {
      case a: Type.Apply => a.argClause.values.zipWithIndex.flatMap { case(v,i) =>
        val role = if(a.argClause.values.size == 1) "" else if(a.tpe.syntax.endsWith("Map")) { if(i == 0) "Key" else "Value" } else "Part" + (i + 1)
        leaves(v,suffix + role)
      }
      case t: Type.Tuple => t.args.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "Part" + (i + 1)) }
      case _ => Nil
    })
    tree.collect { case c: Defn.Class if c.mods.exists(_.is[Mod.Case]) => c }.foreach { c =>
      val ancestry = parents(c)
      val owner = (ancestry.reverse.collect {
        case p: Pkg => p.ref.syntax
        case o: Defn.Object => o.name.value
        case d: Defn.Class => d.name.value
      } :+ c.name.value).mkString(".")
      val companion = c.parent.toList.flatMap(_.children).collectFirst { case o: Defn.Object if o.name.value == c.name.value => o }
      val declarations = scala.collection.mutable.LinkedHashMap.empty[String,String]
      val occupied = companion.toList.flatMap(_.templ.stats).flatMap {
        case d: Defn.Type => List(d.name.value)
        case d: Defn.Object => List(d.name.value)
        case d: Defn.Class => List(d.name.value)
        case d: Defn.Trait => List(d.name.value)
        case d: Defn.Enum => List(d.name.value)
        case _ => Nil
      }.toSet
      c.ctor.paramClauses.filter(_.mod.isEmpty).flatMap(_.values).foreach { p =>
        p.decltpe.toList.flatMap(leaves(_)).foreach { case(t,b,suffix) =>
          val key = owner + "." + p.name.value + (if(suffix.isEmpty) "" else "." + suffix)
          val defaultName = upper(p.name.value) + suffix
          val name = names.getOrElse(key,if(defaultName == c.name.value) "Value" else defaultName)
          require(name.matches("[A-Z][A-Za-z0-9_]*"),"Invalid newtype name: " + key)
          val mappings = reuse.toList.collect { case(pattern,value) if
            key.matches(pattern.split("\\*",-1).map(java.util.regex.Pattern.quote).mkString(".*")) => value }.distinct
          require(mappings.size <= 1,"Conflicting concept mappings: " + key)
          val target = mappings.headOption.filterNot(_ == owner + "." + name)
          val shadowed = tree.collect { case n: Type.Name if n.value == b && n.pos.start != t.pos.start &&
            n.parent.exists {
              case d: Defn.Type => d.name eq n
              case d: Defn.Class => d.name eq n
              case _ => false
            } => n }.nonEmpty
          val blocked = ancestry.exists(_.is[Defn.Def]) || shadowed ||
            (target.isEmpty && (occupied(name) || companion.exists(_.templ.stats.isEmpty) || declarations.get(name).exists(_ != b)))
          if(blocked) evidence += List(path,(t.pos.startLine + 1).toString,"UNRESOLVED " + key,t.structure)
          else {
            val replacement = target.getOrElse(c.name.value + "." + name)
            if(target.isEmpty) declarations(name) = b
            result += ((t.pos.start,t.pos.end,replacement))
            evidence += List(path,(t.pos.startLine + 1).toString,owner + "#" + p.name.value + "/" + suffix + " " + t.structure,replacement)
          }
        }
      }
      if(declarations.nonEmpty) {
        val pad = companion.map(o => indent(o.templ.stats.head)).getOrElse(indent(c) + "  ")
        val definitions = declarations.toList.flatMap { case(n,b) => List(
          "type " + n + " = " + n + ".T",
          "object " + n + " extends _root_.io.github.iltotore.iron.RefinedType[" +
            (if(b == "String") "_root_.java.lang.String" else "_root_.scala." + b) +
            ", _root_.io.github.iltotore.iron.constraint.any.Pure]") }.mkString("\n" + pad)
        companion match {
          case Some(o) => val start = o.templ.stats.head.pos.start; result += ((start,start,definitions + "\n\n" + pad))
          case None => result += ((c.pos.end,c.pos.end,"\n\n" + indent(c) + "object " + c.name.value + ":\n" + pad + definitions))
        }
      }
    }
    result.toList
  }
}
