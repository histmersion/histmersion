import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object AstRecipe {
  case class Consolidation(within: String = "", names: List[String] = Nil, target: String = "", targetOwner: String = "", expected: Int = -1, primitiveEitherLeft: Boolean = false, constructorProperties: Boolean = false, reuseAcross: List[String] = Nil)
  object Consolidation {
    implicit val surface: generic.Surface[Consolidation] = generic.deriveSurface[Consolidation]
    implicit val decoder: ConfDecoder[Consolidation] = generic.deriveDecoder(Consolidation()).noTypos
  }
  case class Edit(path: String = "", before: String = "", after: String = "", count: Int = 1, kind: String = "stat")
  object Edit {
    implicit val surface: generic.Surface[Edit] = generic.deriveSurface[Edit]
    implicit val decoder: ConfDecoder[Edit] = generic.deriveDecoder(Edit()).noTypos
  }
  case class Policy(manifest: String = "", report: String = "", edits: List[Edit] = Nil, creates: Map[String,String] = Map.empty, appendStats: Map[String,String] = Map.empty, relocatedPreview: String = "", appliedPlans: List[String] = Nil,
    definitionRoots: List[String] = Nil, reuse: Map[String,String] = Map.empty, names: Map[String,String] = Map.empty, defer: Map[String,String] = Map.empty, buffers: Map[String,String] = Map.empty, localModels: List[String] = Nil,
    measureTypes: Boolean = false, confirmedTypes: List[String] = Nil, expectedTypeSelectors: List[String] = Nil, memberTypes: Boolean = false, previousTypes: String = "", otherConstructors: Boolean = false,
    signatureBuffers: Boolean = false, bufferHistory: List[String] = Nil, localMemberTypes: Boolean = false, indexNewtypes: Boolean = false,
    expectedNewtypes: List[String] = Nil, expectedOverlap: Map[String,Int] = Map.empty,
    consolidationIndex: String = "", consolidations: List[Consolidation] = Nil, additionalManifest: String = "",
    ironProjects: List[String] = Nil, interfaceContracts: List[String] = Nil,
    compilerObservation: String = "", boundaryRoots: List[String] = Nil, rawInputTargets: List[String] = Nil, errorType: String = "", testComparisons: Boolean = false, triviaFiles: List[String] = Nil, constructionOnly: Boolean = false)
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
    val supplemental = if(policy.additionalManifest.isEmpty) Nil else rows(policy.additionalManifest)
    val initial = policy.appliedPlans.foldLeft((rows(policy.manifest) ++ supplemental).map(r => r(0) -> r(1)).toMap) { (before, plan) =>
      val changes = rows(plan)
      require(changes.forall(_.length == 3) && changes.map(_(0)).distinct.size == changes.size)
      changes.foreach(r => require(if(r(1) == "new") !before.contains(r(0)) else before.get(r(0)).contains(r(1)), "Patch chain does not match manifest: " + r(0)))
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
    require(!Files.exists(out) && (policy.edits.nonEmpty || policy.creates.nonEmpty || policy.appendStats.nonEmpty || policy.definitionRoots.nonEmpty || policy.measureTypes || policy.consolidations.nonEmpty || policy.ironProjects.nonEmpty || policy.interfaceContracts.nonEmpty || policy.compilerObservation.nonEmpty || policy.triviaFiles.nonEmpty))
    require(policy.appendStats.keySet.subsetOf(policy.edits.map(_.path).toSet), "Append requires a reviewed edit in the same file")
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    val consolidated = TypeConsolidation.patches(frozen,policy.consolidationIndex,policy.consolidations,evidence,policy.creates)
    val aligned = InterfaceSignatures.patches(frozen,policy.consolidationIndex,policy.interfaceContracts,evidence,policy.boundaryRoots)
    val repaired = CompilerRepairs.patches(frozen,policy.consolidationIndex,policy.compilerObservation,policy.boundaryRoots,evidence,policy.rawInputTargets,policy.errorType,policy.testComparisons,policy.constructionOnly)
    val definitionPaths = frozen.keys.filter(p => policy.definitionRoots.exists(p.startsWith) && p.contains("/src/main/")).toSet
    val selected = policy.edits.groupBy(_.path) ++ (definitionPaths ++ consolidated.keySet ++ aligned.keySet ++ repaired.keySet ++ policy.triviaFiles.toSet ++ (if(policy.ironProjects.nonEmpty) Set("build.sbt") else Set.empty[String])).filterNot(policy.edits.map(_.path).contains).map(_ -> List.empty[AstRecipe.Edit])
    def parseSource(s: String,path: String): Source = {
      implicit val dialect: Dialect = if(path.endsWith(".sbt")) dialects.Scala3.withAllowToplevelTerms(true) else dialects.Scala3
      s.parse[Source].get
    }
    val bufferTargets = policy.bufferHistory.flatMap(p => rows(p).filter(r => r.length == 4 && r(2).contains("Type.Name(\"Byte\")")).map(r => r(0) -> r(3))).groupBy(_._1).map { case(p,rs) => p -> rs.map(_._2).distinct }
    val targetPaths = bufferTargets.toList.flatMap { case(p,ts) => ts.map(_ -> p) }.toMap
    val externalBufferRefs = if(targetPaths.isEmpty) Map.empty[String,List[String]] else frozen.keys.toList.flatMap { p =>
      val s = read(p)
      if(!targetPaths.keys.exists(s.contains)) Nil else s.parse[Source].get.collect {
        case t: Type.Select if targetPaths.get(t.syntax).exists(_ != p) => t.syntax -> p
      }
    }.groupBy(_._1).map { case(t,rs) => t -> rs.map(_._2).distinct.sorted }
    val changed = selected.toList.sortBy(_._1).flatMap { case(path, recipes) =>
      require(frozen.contains(path))
      val source = read(path)
      val tree = parseSource(source,path)
      val definitionPatches = if(definitionPaths(path)) {
        val records = ModelDefinitions.patches(tree, source, path, policy.reuse, policy.names, policy.defer, policy.buffers, policy.localModels, policy.otherConstructors, evidence)
        val members = if(policy.memberTypes) MemberDefinitions.patches(tree,source,path,policy.reuse,policy.names,policy.signatureBuffers,policy.localMemberTypes,evidence) else Nil
        val corrections = BufferSignatureRepair.patches(tree,path,bufferTargets.getOrElse(path,Nil),externalBufferRefs,evidence)
        (records ++ members ++ corrections).groupBy(p => (p._1,p._2)).toList.flatMap { case((a,b),ps) =>
          if(a == b) {
            val snippets = ps.map(_._3)
            val combined = if(snippets.size > 1 && snippets.forall(s => s.startsWith("\n\n") && s.trim.startsWith("object "))) {
              val headers = snippets.map(s => s.take(s.indexOf(":\n") + 2))
              require(headers.distinct.size == 1,"Conflicting generated companions")
              headers.head + snippets.map(s => s.drop(s.indexOf(":\n") + 2)).mkString("\n")
            } else snippets.mkString("")
            List((a,b,combined))
          } else ps
        }
      } else Nil
      val build = if(path == "build.sbt") IronDependencies.patches(tree,policy.ironProjects,evidence) else Nil
      val trivia = if(!policy.triviaFiles.contains(path)) Nil else tree.tokens.groupBy(_.pos.startLine).values.filter(_.forall(t => t.is[Token.Space] || t.is[Token.LF])).flatten.filter(_.is[Token.Space]).toList.map { t =>
        evidence += List(path,(t.pos.startLine + 1).toString,"TRIVIA " + t.structure,"remove blank-line indentation; AST unchanged")
        (t.pos.start,t.pos.end,"")
      }
      val patches = definitionPatches ++ consolidated.getOrElse(path,Nil) ++ aligned.getOrElse(path,Nil) ++ repaired.getOrElse(path,Nil) ++ build ++ trivia ++ recipes.flatMap { recipe =>
        require(Set("stat","type")(recipe.kind))
        val pattern: Tree = if(recipe.kind == "type") recipe.before.parse[Type].get else recipe.before.parse[Stat].get
        val replacementAst = if(recipe.kind == "type") recipe.after.parse[Type].get.structure else fragment(recipe.after)
        val matches = tree.collect { case t if t.structure == pattern.structure => t }
        require(matches.size == recipe.count && recipe.count > 0, "AST count mismatch: " + path + " " + recipe.before)
        matches.map { t =>
          val prefix = source.substring(source.lastIndexOf('\n', t.pos.start - 1) + 1, t.pos.start)
          val indent = prefix.takeWhile(_ == ' ')
          val replacement = recipe.after.linesIterator.zipWithIndex.map { case(line,i) => if(i == 0 || line.isEmpty) line else indent + line }.mkString("\n")
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
      parseSource(next,path)
      if(source == next) Nil else List((path, source, next))
    }
    val created = policy.creates.toList.sortBy(_._1).map { case(p,s) =>
      require(!Files.exists(Paths.get(p)) && !policy.edits.exists(_.path == p)); s.parse[Source].get; (p,"",s)
    }
    Files.createDirectories(out)
    if(policy.measureTypes) TypeMeasurement.run(frozen.filterNot(_._1.endsWith(".sbt")),out,policy.confirmedTypes,policy.expectedTypeSelectors,policy.previousTypes,policy.indexNewtypes,policy.appliedPlans,policy.expectedNewtypes,policy.expectedOverlap)
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
      val converted = evidence.toList.filter(r => r(2).contains("#") && !r(2).startsWith("UNRESOLVED") && !r(2).startsWith("CORRECTION"))
      val summary = s"Converted primitive leaves: ${converted.size}; models: ${converted.map(_(2).takeWhile(_ != '#')).distinct.size}; blocked primitive leaves: ${evidence.count(_(2).startsWith("UNRESOLVED"))}\n"
      Files.writeString(out.resolve("status.txt"),summary,java.nio.file.StandardOpenOption.APPEND)
      println(summary)
      if(policy.buffers.nonEmpty) Files.writeString(out.resolve("status.txt"),s"Buffer corrections: ${evidence.count(_(2).startsWith("CORRECTION"))}\n",java.nio.file.StandardOpenOption.APPEND)
    }
  }
}

object IronDependencies {
  def patches(tree: Source, projects: List[String], evidence: scala.collection.mutable.ListBuffer[List[String]]): List[(Int,Int,String)] = {
    if(projects.isEmpty) return Nil
    val declarations = tree.collect { case d: Defn.Val if d.pats.exists { case Pat.Var(n) => projects.contains(n.value); case _ => false } => d }
    require(declarations.size == projects.distinct.size,"Missing or duplicate dependency project")
    declarations.flatMap { d =>
      require(d.rhs.collect { case a: Term.Apply if a.fun.syntax.endsWith(".in") => a }.nonEmpty,"Expected a project definition")
      if(d.rhs.collect { case Lit.String("iron") => true }.nonEmpty) Nil else {
        val next = "\n  .settings(libraryDependencies += \"io.github.iltotore\" %% \"iron\" % V.iron)"
        evidence += List("build.sbt",(d.pos.startLine + 1).toString,d.structure,"append project Iron dependency")
        List((d.rhs.pos.end,d.rhs.pos.end,next))
      }
    }
  }
}

class RepairTypes(frozen: Map[String,String], index: String) {
  implicit val dialect: Dialect = dialects.Scala3
  private val indexed = Files.readAllLines(Paths.get(index,"newtypes.tsv")).asScala.drop(1).map(_.split("\t",-1).toList).toList
  private val indexedNames = indexed.groupBy(_(5))
  private val indexedOwners = indexed.groupBy(d => d(5).stripSuffix("." + d(6)))
  private val loaded = scala.collection.mutable.Map.empty[String,Source]
  def tree(path: String): Source = loaded.getOrElseUpdate(path, { require(frozen.contains(path)); Files.readString(Paths.get(path)).parse[Source].get })
  def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  def owner(t: Tree): String = parents(t).reverse.collect {
    case p: Pkg => p.ref.syntax
    case o: Defn.Object => o.name.value
    case c: Defn.Class => c.name.value
    case c: Defn.Trait => c.name.value
    case d: Defn.Def => d.name.value
  }.mkString(".")
  def clean(s: String): String = s.stripPrefix("_root_.") match {
    case "java.lang.String" => "String"
    case s if s.startsWith("scala.") && Set("Boolean","Byte","Short","Int","Long","Float","Double","Char","Unit","Option","Array","List","Set","Map","Either")(s.stripPrefix("scala.")) => s.stripPrefix("scala.")
    case s => s
  }
  case class Pure(name: String, base: String)
  private val pureCache = scala.collection.mutable.Map.empty[String,Option[Pure]]
  def base(t: Type): String = t match {
    case a: Type.Apply => clean(a.tpe.syntax) + a.argClause.values.map(base).mkString("[",",","]")
    case a: Type.Tuple => a.args.map(base).mkString("(",",",")")
    case _ => clean(t.syntax)
  }
  def caseClass(name: String, at: Tree): Option[Defn.Class] = {
    val prefixes = owner(at).split('.').toList.inits.map(_.mkString(".")).filter(_.nonEmpty).toList
    val names = (name.stripPrefix("_root_.") :: prefixes.map(_ + "." + name)).distinct
    names.foreach { n =>
      val suffixes = n.split('.').toList.inits.filter(_.nonEmpty).map(_.mkString("/") + ".scala").toList
      frozen.keys.filter(p => suffixes.exists(s => p.endsWith("/" + s))).foreach(tree)
    }
    val matches = loaded.values.toList.flatMap(_.collect {
      case c: Defn.Class if c.mods.exists(_.is[Mod.Case]) && names.contains(owner(c) + "." + c.name.value) => c
    })
    if(matches.size == 1) Some(matches.head) else None
  }
  private def exact(name: String, seen: Set[String]): Option[Pure] = {
    if(seen(name)) return None
    val direct = indexedNames.getOrElse(name,Nil)
    val enclosing = name.split('.').toList.inits.drop(1).map(_.mkString(".")).find(indexedOwners.contains).toList.flatMap(indexedOwners)
    val paths = (if(direct.nonEmpty) direct else enclosing).map(_(1)).distinct
    val suffixes = name.split('.').toList.inits.filter(_.nonEmpty).map(_.mkString("/" ) + ".scala").toList
    val candidates = (paths ++ frozen.keys.filter(p => suffixes.exists(s => p.endsWith("/" + s)))).distinct.map(tree)
    val aliases = candidates.flatMap(_.collect {
      case d: Defn.Type if owner(d) + "." + d.name.value == name && !d.body.syntax.endsWith(".T") => d.body.syntax.stripPrefix("_root_.")
    }).distinct
    if(aliases.size == 1) return exact(aliases.head,seen + name)
    val objects = candidates.flatMap(_.collect { case o: Defn.Object if owner(o) + "." + o.name.value == name => o })
    if(objects.size != 1) None else {
      val o = objects.head
      if(parents(o).exists(t => t.is[Defn.Def] || t.is[Defn.Class] || t.is[Defn.Trait])) None else o.templ.inits.collectFirst {
        case i if i.tpe.is[Type.Apply] => i.tpe.asInstanceOf[Type.Apply]
      }.filter(a => a.tpe.syntax.stripPrefix("_root_.") == "io.github.iltotore.iron.RefinedType" && a.argClause.values.size == 2 &&
        a.argClause.values(1).syntax.stripPrefix("_root_.") == "io.github.iltotore.iron.constraint.any.Pure" &&
        (a.argClause.values.head.syntax.startsWith("_root_.java.lang.") || a.argClause.values.head.syntax.startsWith("_root_.scala."))).map(a => Pure(name,base(a.argClause.values.head)))
    }
  }
  def pure(s: String, at: Tree): Option[Pure] = {
    val raw = s.stripPrefix("_root_.").stripSuffix(".T")
    val prefixes = owner(at).split('.').toList.inits.map(_.mkString(".")).filter(_.nonEmpty).toList
    (raw :: prefixes.map(_ + "." + raw)).distinct.iterator.map(n => pureCache.getOrElseUpdate(n,exact(n,Set.empty))).find(_.nonEmpty).flatten
  }
  def shape(t: Type, at: Tree): String = t match {
    case a: Type.Apply => clean(a.tpe.syntax) + a.argClause.values.map(shape(_,at)).mkString("[",",","]")
    case _ => pure(t.syntax,at).map(_.base).getOrElse(clean(t.syntax))
  }
  def qualified(t: Type): String = t match {
    case a: Type.Apply => (if(clean(a.tpe.syntax) == "Option") "_root_.scala.Option" else a.tpe.syntax) + a.argClause.values.map(qualified).mkString("[",", ","]")
    case _ if pure(t.syntax,t).nonEmpty => "_root_." + pure(t.syntax,t).get.name
    case _: Type.Select => "_root_." + t.syntax.stripPrefix("_root_.")
    case _ => t.syntax
  }
}

object InterfaceSignatures {
  def patches(frozen: Map[String,String], index: String, contracts: List[String], evidence: scala.collection.mutable.ListBuffer[List[String]], roots: List[String] = Nil): Map[String,List[(Int,Int,String)]] = {
    if(contracts.isEmpty) return Map.empty
    val types = new RepairTypes(frozen,index)
    val paths = frozen.keys.filter(p => p.endsWith(".scala") && contracts.exists(c => Files.readString(Paths.get(p)).contains(c.split('.').last))).toList.sorted
    val trees = paths.map(p => p -> types.tree(p))
    val interfaces = trees.flatMap { case(_,tree) => tree.collect {
      case t: Defn.Trait if contracts.contains(types.owner(t) + "." + t.name.value) => (types.owner(t) + "." + t.name.value) -> t
    }}.groupBy(_._1)
    require(contracts.forall(n => interfaces.get(n).exists(_.size == 1)),"Interface contract missing or ambiguous")
    val patches = scala.collection.mutable.ListBuffer.empty[(String,(Int,Int,String))]
    trees.filter { case(path,_) => roots.isEmpty || roots.exists(path.startsWith) }.foreach { case(path,tree) => tree.collect { case template: Template =>
      template.inits.foreach { init =>
        val raw = (init.tpe match { case a: Type.Apply => a.tpe.syntax; case t => t.syntax }).stripPrefix("_root_.")
        val local = tree.collect { case t: Defn.Trait if types.owner(t) + "." + t.name.value == raw || t.name.value == raw => types.owner(t) + "." + t.name.value }.distinct
        val scoped = types.owner(init).split('.').toList.inits.map(_.mkString(".")).filter(_.nonEmpty).map(_ + "." + raw).filter(contracts.contains).toList.distinct
        val resolved = if(contracts.contains(raw)) Some(raw) else if(local.size == 1 && contracts.contains(local.head)) Some(local.head) else if(scoped.size == 1) Some(scoped.head) else None
        resolved.foreach { contract =>
          val parent = interfaces(contract).head._2
          val variables = parent.tparamClause.values.map(_.name.value)
          val arguments = init.tpe match { case a: Type.Apply => a.argClause.values.map(_.syntax); case _ => Nil }
          if(variables != arguments) evidence += List(path,(init.pos.startLine + 1).toString,"UNRESOLVED interface " + contract,"generic substitution requires semantic evidence")
          else {
          val signatures = parent.templ.stats.collect {
            case d: Decl.Def => (d.name.value,d.paramClauseGroups.flatMap(_.paramClauses).map(_.values),d.decltpe)
            case d: Defn.Def if d.decltpe.nonEmpty => (d.name.value,d.paramClauseGroups.flatMap(_.paramClauses).map(_.values),d.decltpe.get)
          }
          template.stats.collect { case d: Defn.Def => d }.foreach { method =>
            val params = method.paramClauseGroups.flatMap(_.paramClauses).map(_.values)
            val matching = signatures.filter(s => s._1 == method.name.value && s._2.map(_.size) == params.map(_.size))
            if(matching.size == 1) {
              val (_,expectedParams,result) = matching.head
              val pairs = params.flatten.zip(expectedParams.flatten).flatMap { case(p,q) => p.decltpe.zip(q.decltpe) } ++ method.decltpe.toList.map(_ -> result)
              val hasNominal = (pairs.map(_._2) :+ result).exists(t => t.collect { case x: Type if types.pure(x.syntax,t).nonEmpty => true }.nonEmpty)
              if(hasNominal && pairs.forall { case(a,b) => types.shape(a,a) == types.shape(b,b) }) {
              if(method.decltpe.isEmpty) {
                val insertion = method.paramClauseGroups.flatMap(_.paramClauses).lastOption.map(_.pos.end).orElse(if(method.paramClauseGroups.isEmpty) Some(method.name.pos.end) else None)
                insertion.foreach { at =>
                  evidence += List(path,(method.pos.startLine + 1).toString,"INTERFACE_RESULT " + contract + "." + method.name.value,types.qualified(result))
                  patches += path -> ((at,at,": " + types.qualified(result)))
                }
              }
              pairs.foreach { case(a,b) =>
                val next = types.qualified(b)
                if(a.syntax.stripPrefix("_root_.") != next.stripPrefix("_root_.")) {
                  evidence += List(path,(a.pos.startLine + 1).toString,"INTERFACE " + contract + "." + method.name.value + " " + a.structure,next)
                  patches += path -> ((a.pos.start,a.pos.end,next))
                }
              }
              }
              else if(hasNominal) evidence += List(path,(method.pos.startLine + 1).toString,"UNRESOLVED interface " + contract + "." + method.name.value,"incompatible parameter/result shape")
            }
          }
          }
        }
      }
    }}
    patches.toList.distinct.groupBy(_._1).map { case(p,rs) => p -> rs.map(_._2) }
  }
}

object CompilerRepairs {
  def patches(frozen: Map[String,String], index: String, observation: String, roots: List[String], evidence: scala.collection.mutable.ListBuffer[List[String]], rawInputs: List[String] = Nil, errorType: String = "", testComparisons: Boolean = false, constructionOnly: Boolean = false): Map[String,List[(Int,Int,String)]] = {
    if(observation.isEmpty) return Map.empty
    implicit val dialect: Dialect = dialects.Scala3
    require(roots.nonEmpty,"Compiler repairs need explicit boundary roots")
    require(!constructionOnly || (rawInputs.isEmpty && errorType.isEmpty && !testComparisons),"Construction-only mode excludes nominal, error-payload and comparison adapters")
    val snapshot = Files.readAllLines(Paths.get(observation,"sources.tsv")).asScala.drop(1).map(_.split("\t",-1)).map(r => r(0) -> r(1)).toMap
    require(snapshot == frozen,"Compiler diagnostics do not describe the current frozen snapshot")
    val text = Files.readString(Paths.get(observation,"build.log")).replaceAll("\u001b\\[[;\\d]*m","")
    val header = "(?m)^.*-- \\[(E[0-9]+)\\].*?: (.+\\.scala):([0-9]+):([0-9]+).*$".r
    val diagnostics = header.findAllMatchIn(text).toList
    val unclassifiedHeaders = "(?m)^.*-- (?:Error|Warning): (.+\\.scala):([0-9]+):([0-9]+).*$".r.findAllMatchIn(text).toList
    val boundaries = (diagnostics.map(_.start) ++ unclassifiedHeaders.map(_.start)).sorted
    require(diagnostics.nonEmpty,"No positioned compiler diagnostics parsed; do not infer a clean build")
    val types = new RepairTypes(frozen,index)
    val root = Paths.get("").toAbsolutePath.normalize
    unclassifiedHeaders.foreach { d =>
      val absolute = Paths.get(d.group(1)).toAbsolutePath.normalize
      if(absolute.startsWith(root)) {
        val path = root.relativize(absolute).toString
        if(frozen.contains(path) && roots.exists(path.startsWith)) evidence += List(path,d.group(2),"UNRESOLVED compiler unclassified","Diagnostic has no numeric code; not automatically rewritten; see " + observation)
      }
    }
    val proposed = scala.collection.mutable.ListBuffer.empty[(String,Tree,String,String)]
    val patternGuards = scala.collection.mutable.LinkedHashMap.empty[(String,Case),List[String]]
    val freshNames = scala.collection.mutable.Map.empty[Tree,Set[String]]
    def fresh(prefix: String, tree: Tree): String = {
      val names = tree.collect { case n: Term.Name => n.value }.toSet ++ freshNames.getOrElse(tree,Set.empty)
      val n = Iterator.from(1).map(i => prefix + (if(i == 1) "" else i.toString)).find(n => !names(n)).get
      freshNames(tree) = freshNames.getOrElse(tree,Set.empty) + n; n
    }
    def guardLiteral(path: String, node: Tree, literal: Lit.String, tree: Tree, reason: String): Unit = {
      val c = types.parents(node).collectFirst { case c: Case if c.pat.pos.start <= node.pos.start && c.pat.pos.end >= node.pos.end => c }.get
      val name = fresh("refactorPattern",tree)
      proposed += ((path,node,name,reason))
      val key = path -> c
      patternGuards(key) = patternGuards.getOrElse(key,Nil) :+ (name + ".value == " + literal.syntax)
    }
    diagnostics.zipWithIndex.foreach { case(d,i) =>
      val abs = Paths.get(d.group(2)).toAbsolutePath.normalize
      require(abs.startsWith(root),"Diagnostic outside worktree")
      val path = root.relativize(abs).toString
      if(frozen.contains(path) && roots.exists(path.startsWith)) {
        val tree = types.tree(path)
        val line = d.group(3).toInt - 1
        val column = d.group(4).toInt
        val source = Files.readString(Paths.get(path))
        val starts = 0 :: source.indices.filter(j => source.charAt(j) == '\n').map(_ + 1).toList
        require(line < starts.size)
        val offset = starts(line) + column
        val block = text.substring(d.end,boundaries.find(_ >= d.end).getOrElse(text.length))
        val displays = "(?m)^.*?\\b([0-9]+)\\s*\\|(.*)$".r.findAllMatchIn(block).toList
        val sourceDisplayMatches = displays.size == 1 && displays.head.group(1).toInt == line + 1 &&
          displays.head.group(2).trim == source.linesIterator.drop(line).next().trim
        val terms = tree.collect { case t: Term if t.pos.start <= offset && (offset < t.pos.end || (d.group(1) == "E172" && offset == t.pos.end)) => t }.sortBy(t => t.pos.end - t.pos.start)
        val highlights = block.linesIterator.flatMap { s =>
          val bar = s.indexOf('|')
          val tail = if(bar < 0) "" else s.substring(bar + 1)
          if(tail.trim.matches("\\^+")) Some((starts(line) + tail.indexOf('^'),tail.trim.length)) else None
        }.toList
        val mismatch = highlights match {
          case List((start,size)) => tree.collect { case t: Term if t.pos.start <= start && t.pos.end >= start + size => t }.sortBy(t => t.pos.end - t.pos.start).headOption
          case _ => None
        }
        def detail(label: String): Option[String] = block.linesIterator.flatMap { s =>
          val marker = label + ":"
          val at = s.indexOf(marker)
          if(at < 0) None else Some(s.substring(at + marker.length).trim)
        }.toList match { case List(value) => Some(value); case _ => None }
        def fullDetail(label: String): Option[String] = {
          val messages = block.linesIterator.filter(_.contains('|')).map(s => s.substring(s.indexOf('|') + 1).trim).toList
          val locations = messages.indices.filter(i => messages(i).startsWith(label + ":")).toList
          locations match {
            case List(at) => Some((messages(at).stripPrefix(label + ":").trim :: messages.drop(at + 1).takeWhile(s => s.nonEmpty && !s.startsWith("Found:") && !s.startsWith("Required:") && !s.startsWith("where:") && !s.startsWith("Note ") && !s.startsWith("longer "))).mkString(" "))
            case _ => None
          }
        }
        def diagnosticType(s: String, nullable: Boolean = false): Option[Type] = {
          val input = s.replace(" : => "," : ")
          val literalBase = input.tokenize.toOption.flatMap { tokens =>
            var depth = 0
            val colon = tokens.find { token =>
              if(token.is[Token.LeftParen]) { depth += 1; false }
              else if(token.is[Token.RightParen]) { depth -= 1; false }
              else token.is[Token.Colon] && depth == 1
            }
            colon.filter(_ => input.startsWith("(") && input.endsWith(")")).flatMap { token =>
              input.substring(token.pos.end,input.length - 1).trim.parse[Term].toOption.collect { case Term.Ascribe(_: Lit,t) => t }
            }
          }
          val renderedAscription = if(input.startsWith("(") && input.endsWith(")") && input.contains(" : ")) {
            val raw = input.substring(input.indexOf(" : ") + 3,input.length - 1).trim
            raw.parse[Type].toOption.orElse(raw.parse[Term].toOption.collect { case Term.Ascribe(_: Lit,t) => t }).orElse {
              if(raw == "scala.collection.immutable.Nil².type") Some("scala.collection.immutable.Nil.type".parse[Type].get) else None
            }
          } else None
          val parsed = input.parse[Term].toOption.collect { case Term.Ascribe(_,t) => t }.orElse(literalBase).orElse(renderedAscription).orElse(input.parse[Type].toOption)
          parsed.map { case a: Type.ApplyInfix if nullable && a.op.value == "|" && a.rhs.syntax == "Null" => a.lhs; case t => t }.map { t =>
            t.syntax.parse[Term].toOption.collect { case Term.Ascribe(_: Lit,base) => base }.getOrElse(t)
          }
        }
        def normalize(t: Type): String = types.pure(t.syntax,tree).map(_.name).getOrElse(types.clean(t.syntax).stripSuffix(".T"))
        def refinedBase(t: Type): Option[Type] = t match {
          case a: Type.Apply if a.tpe.syntax.stripPrefix("_root_.") == "io.github.iltotore.iron.IronType" && a.argClause.values.size == 2 => Some(a.argClause.values.head)
          case _ => None
        }
        def contextual(t: Type): String = t match {
          case a: Type.Apply => types.clean(a.tpe.syntax) + a.argClause.values.map(contextual).mkString("[",",","]")
          case a: Type.Tuple => a.args.map(contextual).mkString("(",",",")")
          case _ =>
            val normalized = normalize(t)
            if(normalized.contains(".")) normalized else {
              val imports = tree.collect { case i: Import => i.importers.flatMap(x => x.importees.collect { case Importee.Name(n) if n.value == normalized => x.ref.syntax + "." + n.value }) }.flatten.distinct
              val pkg = tree.collect { case p: Pkg => p.ref.syntax }.headOption.getOrElse("")
              if(imports.size == 1) imports.head else if(frozen.keys.exists(_.endsWith("/" + (pkg + "." + normalized).replace('.','/') + ".scala"))) pkg + "." + normalized else normalized
            }
        }
        def convert(from: Type, to: Type, value: String): Option[String] = {
          if(normalize(from) == normalize(to) || contextual(from) == contextual(to) || from.syntax == "Nothing" || to.syntax == "Any" || refinedBase(from).exists(b => normalize(b) == normalize(to))) Some(value) else {
            val a = types.pure(from.syntax,tree); val b = types.pure(to.syntax,tree)
            if(a.isEmpty && b.exists(_.base == types.base(from))) b.map(n => "_root_." + n.name + "(" + value + ")")
            else if(a.isEmpty && types.clean(from.syntax) == "Null" && b.exists(_.base == "String")) b.map(n => "_root_." + n.name + "(" + value + ")")
            else if(a.isEmpty && types.clean(from.syntax) == "Int" && b.exists(_.base == "Long")) b.map(n => "_root_." + n.name + "((" + value + ").toLong)")
            else if(!constructionOnly && b.isEmpty && a.exists(n => n.base == types.clean(to.syntax) || (n.base == "String" && Set("CharSequence","java.lang.CharSequence")(to.syntax)))) Some("(" + value + ").value")
            else if(!constructionOnly && a.nonEmpty && b.exists(n => n.base == a.get.base && rawInputs.contains(n.name))) b.map(n => "_root_." + n.name + "((" + value + ").value)")
            else (from,to) match {
              case (_,t: Type.Apply) if types.clean(t.tpe.syntax) == "Option" && from.syntax == "None.type" => Some(value)
              case (_,t: Type.Apply) if types.clean(t.tpe.syntax) == "List" && Set("Nil.type","scala.collection.immutable.Nil.type","scala.collection.immutable.Nil².type")(from.syntax) => Some(value)
              case (f: Type.Apply,t: Type.Apply) if (types.clean(f.tpe.syntax) == types.clean(t.tpe.syntax) || (types.clean(f.tpe.syntax) == "Some" && types.clean(t.tpe.syntax) == "Option") || (Set("Left","Right")(types.clean(f.tpe.syntax)) && types.clean(t.tpe.syntax) == "Either")) && f.argClause.values.size == t.argClause.values.size =>
                val fa = f.argClause.values; val ta = t.argClause.values
                if(Set("List","Option","Some","Set")(types.clean(f.tpe.syntax)) && fa.size == 1) {
                  val name = fresh("refactorValue",tree)
                  convert(fa.head,ta.head,name).map(body => "(" + value + ").map(" + name + " => " + body + ")")
                } else if(Set("Either","Left","Right")(types.clean(f.tpe.syntax)) && fa.size == 2) {
                  val left = fresh("refactorError",tree); val right = fresh("refactorValue",tree)
                  for(l <- convert(fa.head,ta.head,left); r <- convert(fa(1),ta(1),right)) yield {
                    val first = if(l == left) value else "(" + value + ").left.map(" + left + " => " + l + ")"
                    if(r == right) first else "(" + first + ").map(" + right + " => " + r + ")"
                  }
                } else if(types.clean(f.tpe.syntax) == "Map" && fa.size == 2) {
                  val key = fresh("refactorKey",tree); val item = fresh("refactorItem",tree)
                  for(k <- convert(fa.head,ta.head,key); v <- convert(fa(1),ta(1),item)) yield
                    "(" + value + ").iterator.map { case (" + key + ", " + item + ") => (" + k + ", " + v + ") }.toMap"
                } else None
              case (f: Type.Tuple,t: Type.Tuple) if f.args.size == t.args.size =>
                val name = fresh("refactorTuple",tree)
                val parts = f.args.zip(t.args).zipWithIndex.map { case((a,b),i) => convert(a,b,name + "._" + (i + 1)) }
                if(parts.forall(_.nonEmpty)) Some("{ val " + name + " = " + value + "; " + parts.flatten.mkString("(",", ",")") + " }") else None
              case (f: Type.Function,t: Type.Function) if f.paramClause.values.size == t.paramClause.values.size =>
                val fn = fresh("refactorFunction",tree)
                val parameters = t.paramClause.values.map(_ => fresh("refactorValue",tree))
                val args = t.paramClause.values.zip(f.paramClause.values).zip(parameters).map { case((a,b),n) => convert(a,b,n) }
                if(args.forall(_.nonEmpty)) convert(f.res,t.res,fn + args.flatten.mkString("(",", ",")")).map(body =>
                  "{ val " + fn + " = " + value + "; " + parameters.mkString("(",", ",")") + " => " + body + " }") else None
              case _ => None
            }
          }
        }
        def simple(s: String, nullable: Boolean = false): Option[String] = {
          val raw = if(s.startsWith("(") && s.contains(" : ")) s.substring(s.indexOf(" : ") + 3).stripSuffix(")") else s
          val value = if(nullable) raw.stripSuffix(" | Null") else raw
          if(value.matches("[_A-Za-z][_A-Za-z0-9.]*")) Some(value) else None
        }
        var supported = false
        if(testComparisons && d.group(1) == "E172" && block.contains("munit.Compare") && block.contains("Can't compare these two types:")) {
          def comparisonType(label: String): Option[Type] = {
            val messages = block.linesIterator.filter(_.contains('|')).map(s => s.substring(s.indexOf('|') + 1).trim).toList
            val at = messages.indexWhere(_.startsWith(label + " type:"))
            if(at < 0) None else diagnosticType((messages(at).stripPrefix(label + " type:").trim :: messages.drop(at + 1).takeWhile(s =>
              s.nonEmpty && !s.startsWith("Second type:") && !s.startsWith("Possible "))).mkString(" "))
          }
          def projection(t: Type): Type = if(refinedBase(t).nonEmpty) refinedBase(t).get else types.pure(t.syntax,tree) match {
            case Some(n) => n.base.parse[Type].get
            case None => t match {
              case t if t.syntax == "None.type" => "Option[Nothing]".parse[Type].get
              case t if Set("Nil.type","scala.collection.immutable.Nil.type","scala.collection.immutable.Nil².type")(t.syntax) => "List[Nothing]".parse[Type].get
              case a: Type.Apply if Set("List","Option","Some","Set","Map","Either","Left","Right")(types.clean(a.tpe.syntax)) =>
                val container = types.clean(a.tpe.syntax) match { case "Some" => "Option"; case "Left" | "Right" => "Either"; case n => n }
                (container + a.argClause.values.map(projection).map(_.syntax).mkString("[",", ", "]")).parse[Type].get
              case a: Type.Tuple => a.args.map(projection).map(_.syntax).mkString("(",", ", ")").parse[Type].get
              case _ => t
            }
          }
          def comparable(a: Type,b: Type): Boolean = normalize(a) == normalize(b) || b.syntax == "Nothing" || ((a,b) match {
            case (x: Type.Apply,y: Type.Apply) if types.clean(x.tpe.syntax) == "Map" && types.clean(y.tpe.syntax) == "Map" && x.argClause.values.size == 2 && y.argClause.values.size == 2 && y.argClause.values(1).syntax == "Nothing" => true
            case (x: Type.Apply,y: Type.Apply) if normalize(x.tpe) == normalize(y.tpe) && x.argClause.values.size == y.argClause.values.size => x.argClause.values.zip(y.argClause.values).forall { case(c,d) => comparable(c,d) }
            case (x: Type.Tuple,y: Type.Tuple) if x.args.size == y.args.size => x.args.zip(y.args).forall { case(c,d) => comparable(c,d) }
            case _ => false
          })
          val comparison = tree.collect { case a: Term.Apply if a.fun.syntax == "assertEquals" && a.argClause.values.size >= 2 &&
            a.pos.start <= offset && offset <= a.pos.end => a }.sortBy(a => a.pos.end - a.pos.start).headOption
          for(a <- comparison; first <- comparisonType("First"); second <- comparisonType("Second") if
            comparable(projection(first),projection(second)) && second.collect { case t: Type if types.pure(t.syntax,tree).nonEmpty => true }.isEmpty &&
            displays.nonEmpty && displays.forall(m => m.group(1).toInt <= starts.size && (m.group(2).trim == "..." || m.group(2).trim == source.linesIterator.drop(m.group(1).toInt - 1).next().trim))) {
            val obtained = a.argClause.values.head
            val original = source.substring(obtained.pos.start,obtained.pos.end)
            convert(first,projection(first),original).filter(_ != original).foreach { next =>
              proposed += ((path,obtained,"(" + next + ": " + projection(first).syntax + ")","E172 test comparison Pure projection")); supported = true
            }
          }
        }
        if(d.group(1) == "E007") {
          for(found <- fullDetail("Found").flatMap(s => diagnosticType(s)); required <- fullDetail("Required").flatMap(s => diagnosticType(s,nullable = true))) {
            val body = tree.collect { case m: Defn.Def if displays.nonEmpty && m.body.pos.startLine + 1 == displays.head.group(1).toInt && m.body.pos.endLine + 1 == displays.last.group(1).toInt && m.decltpe.exists(t => contextual(t) == contextual(required)) => m.body }.headOption
            val target = if(sourceDisplayMatches) mismatch else if(displays.nonEmpty && displays.forall(m => m.group(1).toInt <= starts.size && (m.group(2).trim == "..." || m.group(2).trim == source.linesIterator.drop(m.group(1).toInt - 1).next().trim))) {
              body.orElse(tree.collect { case t: Term if displays.head.group(1).toInt < displays.last.group(1).toInt && t.pos.startLine + 1 == displays.head.group(1).toInt && t.pos.endLine + 1 == displays.last.group(1).toInt => t }.sortBy(t => -(t.pos.end - t.pos.start)).headOption)
            } else None
            target.filterNot(t => types.parents(t).exists(_.is[Pat]) || t.parent.exists {
              case d: Defn.Def => d.name eq t
              case d: Decl.Def => d.name eq t
              case o: Defn.Object => o.name eq t
              case e: Defn.EnumCase => e.name eq t
              case p: Term.Param => p.name eq t
              case _ => false
            }).foreach { t =>
            val original = source.substring(t.pos.start,t.pos.end)
            convert(found,required,original).filter(_ != original).foreach { n => proposed += ((path,t,n,"E007 " + found.syntax + " -> " + required.syntax)); supported = true }
            }
          }
        }
        if(!constructionOnly && sourceDisplayMatches && d.group(1) == "E008") {
          val member = "value ([^ ]+) is not a member of ([^\\r\\n]+)".r.findFirstMatchIn(block)
          def reported(m: scala.util.matching.Regex.Match): String = m.group(2).split(", but",2).head.trim.stripSuffix(".")
          member.foreach { m =>
            val union = reported(m).parse[Type].toOption.collect { case t: Type.ApplyInfix if t.op.value == "|" => t }
            val nominal = union.toList.flatMap(t => List(t.lhs -> t.rhs,t.rhs -> t.lhs)).collectFirst {
              case (a,b) if types.clean(b.syntax) == "String" && types.pure(a.syntax,tree).exists(_.base == "String") => types.pure(a.syntax,tree).get
            }
            for(n <- nominal; selection <- terms.collectFirst { case s: Term.Select if s.name.value == m.group(1) => s }) {
              selection.qual match {
                case fallback: Term.Apply if fallback.argClause.values.size == 1 && fallback.argClause.values.head.is[Lit.String] => fallback.fun match {
                  case Term.Select(option: Term.Apply,Term.Name("getOrElse")) if Set("Option","scala.Option")(option.fun.syntax) && option.argClause.values.size == 1 => option.argClause.values.head match {
                    case arg: Term.Name if parameterType(arg,selection).flatMap(types.pure(_,selection)).exists(_.name == n.name) =>
                      val original = source.substring(fallback.pos.start,fallback.pos.end)
                      val next = original.patch(arg.pos.start - fallback.pos.start,"(" + arg.syntax + ").value",arg.pos.end - arg.pos.start)
                      val target: Tree = if(m.group(1) == "value") selection else fallback
                      proposed += ((path,target,next,"E008 nullable Pure/raw fallback")); supported = true
                    case _ => ()
                  }
                  case _ => ()
                }
                case _ => ()
              }
            }
          }
          member.foreach { m => types.pure(reported(m),tree).foreach { n =>
            val method = m.group(1)
            val encoded = scala.reflect.NameTransformer.encode(method)
            val stringMember = n.base == "String" && (method == "+" || (classOf[String].getMethods ++ classOf[scala.collection.StringOps].getMethods).exists(_.getName == encoded))
            val receiver = if(stringMember) terms.collectFirst {
              case s: Term.Select if s.name.value == method => s.qual
              case a: Term.ApplyInfix if a.op.value == method => a.lhs
            } else if(n.base == "Boolean" && method == "unary_!") terms.collectFirst { case a: Term.ApplyUnary if a.op.value == "!" => a.arg } else None
            receiver.foreach { r => proposed += ((path,r,"(" + source.substring(r.pos.start,r.pos.end) + ").value","E008 primitive member " + method)); supported = true }
          }}
        }
        if(!constructionOnly && sourceDisplayMatches && d.group(1) == "E049") {
          tree.collect { case p: Pat.Extract if p.fun.is[Term.Name] && p.fun.pos.start <= offset && offset < p.fun.pos.end => p }.foreach { p =>
            types.parents(p).collectFirst { case e: Defn.Enum => e }.foreach { e =>
              if(e.templ.stats.collect { case c: Defn.EnumCase if c.name.value == p.fun.syntax => c }.size == 1 && block.contains("Reference to " + p.fun.syntax + " is ambiguous")) {
                proposed += ((path,p.fun,"_root_." + types.owner(e) + "." + e.name.value + "." + p.fun.syntax,"E049 enclosing enum case")); supported = true
              }
            }
          }
        }
        if(sourceDisplayMatches && d.group(1) == "E134") {
          terms.collectFirst { case a: Term.Apply => a }.foreach { app =>
            app.argClause.values match {
              case List(f: Term.AnonymousFunction) if !constructionOnly && f.body.structure == Term.Select(Term.Placeholder(),Term.Name("value")).structure =>
                val name = fresh("refactorValue",tree)
                val call = source.substring(app.pos.start,app.pos.end).patch(f.pos.start - app.pos.start,name + ".value",f.pos.end - f.pos.start)
                proposed += ((path,app,"(" + name + " => " + call + ")","E134 lift placeholder boundary callback")); supported = true
              case _ => ()
            }
            val at = block.indexOf("match arguments")
            val description = if(at < 0) "" else block.substring(at + "match arguments".length).linesIterator.map(s => if(s.contains('|')) s.substring(s.indexOf('|') + 1).trim else s.trim).mkString(" ").trim
            def parts(s: String): List[String] = {
              var depth = 0; var start = 0; val out = scala.collection.mutable.ListBuffer.empty[String]
              s.indices.foreach { i => s.charAt(i) match {
                case '(' | '[' => depth += 1
                case ')' | ']' => depth -= 1
                case ',' if depth == 0 => out += s.substring(start,i).trim; start = i + 1
                case _ => ()
              }}
              out += s.substring(start).trim; out.toList
            }
            var end = -1; var depth = 0
            if(description.startsWith("(")) description.indices.iterator.takeWhile(_ => end < 0).foreach { i =>
              if(description.charAt(i) == '(') depth += 1
              if(description.charAt(i) == ')') { depth -= 1; if(depth == 0) end = i }
            }
            val actuals = if(end < 0) Nil else parts(description.substring(1,end))
            if(!supported && actuals.size == app.argClause.values.size) types.caseClass(app.fun.syntax,app).foreach { record =>
              val fullName = types.owner(record) + "." + record.name.value
              val primary = if(record.tparamClause.values.isEmpty && record.ctor.paramClauses.size == 1 &&
                !record.ctor.mods.exists(m => m.is[Mod.Private] || m.is[Mod.Protected])) List(record.ctor.paramClauses.head.values) else Nil
              val factories = record.parent.toList.flatMap(_.children).collect {
                case o: Defn.Object if types.owner(o) + "." + o.name.value == fullName => o
              }.flatMap(_.templ.stats.collect {
                case m: Defn.Def if m.name.value == "apply" && !m.mods.exists(x => x.is[Mod.Private] || x.is[Mod.Protected]) &&
                  m.paramClauseGroups.flatMap(_.paramClauses).size == 1 && m.collect { case _: Type.Param => true }.isEmpty &&
                  m.decltpe.exists(t => Set(record.name.value,fullName,"_root_." + fullName)(t.syntax)) => m.paramClauseGroups.flatMap(_.paramClauses).head.values
              })
              if(block.contains("of method apply in object " + record.name.value + " with types")) {
                val beforeNames = freshNames.getOrElse(tree,Set.empty[String])
                val attempts = (primary ++ factories).flatMap { parameters =>
                  freshNames(tree) = beforeNames
                  val bound = scala.collection.mutable.Set.empty[Int]
                  var named = false
                  val conversions = app.argClause.values.zip(actuals).zipWithIndex.map { case((argument,actual),ordinal) =>
                    val (index,value) = argument match {
                      case Term.Assign(n: Term.Name,rhs) => named = true; (parameters.indexWhere(_.name.value == n.value),rhs)
                      case t if !named => (ordinal,t)
                      case t => (-1,t)
                    }
                    if(index < 0 || index >= parameters.size || bound(index)) None else {
                      bound += index
                      for(target <- parameters(index).decltpe; found <- diagnosticType(actual); next <- convert(found,types.qualified(target).parse[Type].get,source.substring(value.pos.start,value.pos.end)))
                        yield (value,next)
                    }
                  }
                  if(conversions.forall(_.nonEmpty) && parameters.indices.filterNot(bound).forall(i => parameters(i).default.nonEmpty))
                    Some(conversions.flatten -> freshNames.getOrElse(tree,Set.empty[String])) else None
                }
                freshNames(tree) = beforeNames
                val distinct = attempts.groupBy { case(changes,_) => changes.map { case(t,next) => (t.pos.start,t.pos.end,next) } }.values.map(_.head).toList
                if(distinct.size == 1 && distinct.head._1.exists { case(t,next) => source.substring(t.pos.start,t.pos.end) != next }) {
                  freshNames(tree) = distinct.head._2
                  distinct.head._1.filter { case(t,next) => source.substring(t.pos.start,t.pos.end) != next }.foreach { case(t,next) =>
                    proposed += ((path,t,next,"E134 agreed case-class overload conversion"))
                  }
                  supported = true
                }
              }
            }
            if(!constructionOnly && !supported && actuals.size == app.argClause.values.size) actuals.zip(app.argClause.values).foreach { case(actual,arg) =>
              diagnosticType(actual).flatMap(t => types.pure(t.syntax,tree)).foreach { n =>
                val signatures = if(at < 0) "" else block.take(at)
                val base = if(n.base == "String") "(?:String|java.lang.String|CharSequence|java.lang.CharSequence)" else java.util.regex.Pattern.quote(n.base)
                val parameter = ("\\([^\\n)]*:\\s*" + base + "(?:[,* )]).*").r
                if(signatures.linesIterator.exists(s => parameter.findFirstIn(s).nonEmpty)) {
                  if(arg.is[Term.Placeholder]) types.parents(app).collectFirst { case f: Term.AnonymousFunction if f.body.collect { case _: Term.Placeholder => true }.size == 1 => f }.foreach { f =>
                    val name = fresh("refactorValue",tree)
                    val body = source.substring(f.body.pos.start,f.body.pos.end).patch(arg.pos.start - f.body.pos.start,name + ".value",arg.pos.end - arg.pos.start)
                    proposed += ((path,f,"(" + name + " => " + body + ")","E134 primitive callback argument")); supported = true
                  }
                  else { proposed += ((path,arg,"(" + source.substring(arg.pos.start,arg.pos.end) + ").value","E134 primitive overload argument")); supported = true }
                }
              }
            }
          }
        }
        if(!constructionOnly && sourceDisplayMatches && d.group(1) == "E172") {
          if(testComparisons) {
            val pair = "Values of types ([_A-Za-z][_A-Za-z0-9.]*) and ([_A-Za-z][_A-Za-z0-9.]*) cannot be compared".r.findFirstMatchIn(block)
            for {
              p <- pair
              name <- List((p.group(1),p.group(2)),(p.group(2),p.group(1))).collectFirst { case (nominal,"String") => nominal }
              nominal <- types.pure(name,tree).filter(_.base == "String")
              equality <- terms.collectFirst { case a: Term.ApplyInfix if Set("==","!=")(a.op.value) && a.argClause.values.size == 1 => a }
              literal <- (equality.lhs :: equality.argClause.values).collectFirst { case s: Lit.String => s }
            } { proposed += ((path,literal,"_root_." + nominal.name + "(" + literal.syntax + ")","E172 typed comparison literal")); supported = true }
          }
          val comparison = "Values of types String and ([_A-Za-z][_A-Za-z0-9.]*) cannot be compared".r.findFirstMatchIn(block)
          for {
            name <- comparison.map(_.group(1))
            literal <- terms.collectFirst { case s: Lit.String if types.parents(s).exists { case c: Case => c.pat.pos.start <= s.pos.start && c.pat.pos.end >= s.pos.end; case _ => false } => s }
            nominal <- types.pure(name,literal).filter(_.base == "String")
          } {
            guardLiteral(path,literal,literal,tree,"E172 typed literal guard")
            supported = true
          }
          if(!supported) for {
            name <- comparison.map(_.group(1))
            nominal <- types.pure(name,tree).filter(_.base == "String")
            equality <- terms.collectFirst { case a: Term.ApplyInfix if Set("==","!=")(a.op.value) => a }
            operand <- (equality.lhs :: equality.argClause.values).collectFirst {
              case n: Term.Name if parameterType(n,equality).flatMap(types.pure(_,equality)).exists(_.name == nominal.name) => n
            }
          } { proposed += ((path,operand,"(" + operand.syntax + ").value","E172 primitive boundary equality")); supported = true }
          if(!supported) terms.collectFirst { case s: Term.Select if s.name.value == "flatten" => s }.foreach { flatten =>
            val expected = types.parents(flatten).collectFirst { case m: Defn.Def if m.body eq flatten => m.decltpe }.flatten
            expected.collect { case a: Type.Apply if a.tpe.syntax == "Option" && a.argClause.values.size == 1 => a.argClause.values.head }.flatMap(t => types.pure(t.syntax,t)).foreach { n =>
              if(Set("Option","Some").exists(container => block.contains("Cannot prove that " + container + "[" + n.base + "] <:< Option["))) {
                val name = fresh("refactorValue",tree)
                val base = if(n.base == "String") "_root_.java.lang.String" else "_root_.scala." + n.base
                proposed += ((path,flatten,"(" + source.substring(flatten.pos.start,flatten.pos.end) + "[" + base + "]).map(" + name + " => _root_." + n.name + "(" + name + "))","E172 optional Pure result")); supported = true
              }
            }
          }
        }
        if(!constructionOnly && sourceDisplayMatches && d.group(1) == "E165") {
          val reported = "unmatchable type ([_A-Za-z][_A-Za-z0-9.]*)".r.findFirstMatchIn(block).map(_.group(1))
          tree.collect { case p: Pat.Extract if p.pos.start <= offset && offset < p.pos.end => p }.sortBy(p => p.pos.end - p.pos.start).headOption.foreach { p =>
            p.argClause.values match {
              case List(literal: Lit.String) if reported.flatMap(types.pure(_,p)).exists(n => n.base == "String" && types.pure(p.fun.syntax,p).exists(_.name == n.name)) =>
                guardLiteral(path,p,literal,tree,"E165 typed literal guard"); supported = true
              case _ => ()
            }
          }
        }
        if(!supported) evidence += List(path,(line + 1).toString,"UNRESOLVED compiler " + d.group(1),
          "source-display=" + sourceDisplayMatches + "; found=" + detail("Found").toString + "; required=" + detail("Required").toString +
          "; term=" + terms.headOption.map(_.structure).toString + "; unsupported type, expression, member or diagnostic shape; see " + observation)
      }
    }
    def parameterType(n: Term.Name, at: Tree): Option[String] = types.parents(at).collectFirst {
      case m: Defn.Def if m.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).exists(_.name.value == n.value) =>
        val shadowed = m.body.collect {
          case p: Pat.Var if p.name.value == n.value => true
          case p: Term.Param if p.name.value == n.value => true
        }.nonEmpty
        if(shadowed) None else m.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).find(_.name.value == n.value).flatMap(_.decltpe).map(_.syntax)
    }.flatten
    val primitiveClasses = Map("String" -> classOf[String],"Int" -> java.lang.Integer.TYPE,"Long" -> java.lang.Long.TYPE,
      "Double" -> java.lang.Double.TYPE,"Float" -> java.lang.Float.TYPE,"Boolean" -> java.lang.Boolean.TYPE,"Char" -> java.lang.Character.TYPE,
      "Byte" -> java.lang.Byte.TYPE,"Short" -> java.lang.Short.TYPE)
    def rawString(t: Term): Boolean = t match {
      case _: Lit.String => true
      case i: Term.Interpolate => Set("s","f","raw")(i.prefix.value)
      case _ => false
    }
    if(errorType.nonEmpty) frozen.keys.filter(p => p.endsWith(".scala") && roots.exists(p.startsWith)).foreach { path =>
      val tree = types.tree(path)
      val error = types.pure(errorType,tree).filter(_.base == "String").getOrElse(throw new IllegalArgumentException("Expected Pure String error type"))
      tree.collect { case a: Term.Apply if !types.parents(a).exists {
        case outer: Term.Apply => outer.fun.syntax == "assertEquals"
        case _ => false
      } =>
        val payload = if(Set("Either.cond","scala.Either.cond")(a.fun.syntax) && a.argClause.values.size == 3) a.argClause.values.lift(2)
          else if(a.fun.syntax == "Left" && a.argClause.values.size == 1) a.argClause.values.headOption
          else a.fun match {
            case s: Term.Select if s.name.value == "toRight" && a.argClause.values.size == 1 => a.argClause.values.headOption
            case s: Term.Select if s.name.value == "filterOrElse" && a.argClause.values.size == 2 => a.argClause.values.lift(1)
            case Term.Select(Term.Select(q,Term.Name("left")),Term.Name("map")) if a.argClause.values.size == 1 => a.argClause.values.head match {
              case f: Term.Function if rawString(f.body) => Some(f.body)
              case f: Term.Function if f.paramClause.values.size == 1 && q.is[Term.Select] && q.asInstanceOf[Term.Select].name.value == "toEither" &&
                q.asInstanceOf[Term.Select].qual.is[Term.Apply] && Set("Try","scala.util.Try")(q.asInstanceOf[Term.Select].qual.asInstanceOf[Term.Apply].fun.syntax) &&
                f.body.structure == ("Option(" + f.paramClause.values.head.name.syntax + ".getMessage).getOrElse(" + f.paramClause.values.head.name.syntax + ".getClass.getSimpleName)").parse[Term].get.structure =>
                  val value = f.body
                  proposed += ((path,value,"_root_." + error.name + "(" + Files.readString(Paths.get(path)).substring(value.pos.start,value.pos.end) + ")","ERROR_ROLE Throwable message"))
                  None
              case _ => None
            }
            case _ => None
          }
        payload.filter(rawString).foreach { value => proposed += ((path,value,"_root_." + error.name + "(" + Files.readString(Paths.get(path)).substring(value.pos.start,value.pos.end) + ")","ERROR_ROLE primitive failure payload")) }
      }
    }
    frozen.keys.filter(p => p.endsWith(".scala") && roots.exists(p.startsWith)).foreach { path => types.tree(path).collect {
      case app: Term.Apply if app.argClause.values.size == 1 => app.fun match {
        case Term.Select(receiver: Term.Name,method) => app.argClause.values.head match {
          case arg: Term.Name => for {
            receiverName <- parameterType(receiver,app).filter(_.startsWith("java."))
            argumentType <- parameterType(arg,app).flatMap(types.pure(_,app))
            base <- primitiveClasses.get(argumentType.base)
            receiverClass <- scala.util.Try(Class.forName(receiverName,false,getClass.getClassLoader)).toOption
            if receiverClass.getMethods.exists(m => m.getName == method.value && m.getParameterTypes.toList == List(base))
          } proposed += ((path,arg,"(" + arg.syntax + ").value","JDK boundary primitive overload " + receiverName + "." + method.value))
          case _ => ()
        }
        case _ => ()
      }
    }}
    val guardPatches = patternGuards.toList.map { case((path,c),guards) =>
      val source = Files.readString(Paths.get(path))
      val condition = guards.map(s => "(" + s + ")").mkString(" && ")
      val patch = c.cond match {
        case Some(old) => (old.pos.start,old.pos.end,condition + " && (" + source.substring(old.pos.start,old.pos.end) + ")")
        case None => (c.pat.pos.end,c.pat.pos.end," if " + condition)
      }
      evidence += List(path,(c.pos.startLine + 1).toString,"PATTERN_GUARD " + c.pat.structure,patch._3)
      path -> patch
    }
    val accepted = scala.collection.mutable.ListBuffer.empty[(String,Tree,String,String)]
    proposed.toList.groupBy(p => (p._1,p._2.pos.start,p._2.pos.end,p._3)).values.map(_.head).toList.sortBy(p => (p._2.pos.end - p._2.pos.start,p._1,p._2.pos.start)).foreach { p =>
      if(!accepted.exists(q => p._1 == q._1 && p._2.pos.start < q._2.pos.end && q._2.pos.start < p._2.pos.end)) accepted += p
      else evidence += List(p._1,(p._2.pos.startLine + 1).toString,"UNRESOLVED compiler overlapping expression","innermost repair first; recompile before retry")
    }
    (accepted.toList.map { case(path,t,next,reason) =>
      evidence += List(path,(t.pos.startLine + 1).toString,"COMPILER " + reason + " " + t.structure,next)
      path -> ((t.pos.start,t.pos.end,next))
    } ++ guardPatches).groupBy(_._1).map { case(p,rs) => p -> rs.map(_._2) }
  }
}

object TypeConsolidation {
  def patches(frozen: Map[String,String], index: String, roles: List[AstRecipe.Consolidation], evidence: scala.collection.mutable.ListBuffer[List[String]], creates: Map[String,String] = Map.empty): Map[String,List[(Int,Int,String)]] = {
    if(roles.isEmpty) return Map.empty
    implicit val dialect: Dialect = dialects.Scala3
    def rows(file: String) = Files.readAllLines(Paths.get(file)).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val snapshot = rows(index + "/sources.tsv").map(r => r.head -> r(1)).toMap
    require(snapshot.keySet.subsetOf(frozen.keySet),"Consolidation index contains sources outside the frozen universe")
    val current = new NewtypeIndex(Nil, "")
    val currentTrees = frozen.keys.filter(_.endsWith(".scala")).toList.sorted.map { p =>
      val tree = Files.readString(Paths.get(p)).parse[Source].get
      current.inspect(tree,p,p.stripPrefix("modules/").split("/src/").head,if(p.contains("/src/test/")) "test" else "production")
      p -> tree
    }.toMap
    val additions = new NewtypeIndex(Nil, "")
    val newTrees = creates.map { case(p,s) =>
      require(!Files.exists(Paths.get(p)),"Canonical source already exists: " + p)
      val tree = s.parse[Source].get
      additions.inspect(tree,p,p.stripPrefix("modules/").split("/src/").head,if(p.contains("/src/test/")) "test" else "production")
      p -> tree
    }
    val definitions = current.entries ++ additions.entries
    require(additions.entries.map(_(5)).distinct.size == additions.entries.size && !current.entries.exists(r => additions.entries.exists(_(5) == r(5))),"Created canonical definition already exists or is ambiguous")
    val resolver = new RepairTypes(frozen,index)
    def forwardingPairs(from: Type, to: Type): List[(String,String)] = (from,to) match {
      case (a: Type.Apply,b: Type.Apply) if a.tpe.syntax == b.tpe.syntax && a.argClause.values.size == b.argClause.values.size =>
        a.argClause.values.zip(b.argClause.values).flatMap { case(x,y) => forwardingPairs(x,y) }
      case (a: Type.Tuple,b: Type.Tuple) if a.args.size == b.args.size => a.args.zip(b.args).flatMap { case(x,y) => forwardingPairs(x,y) }
      case _ => (for(a <- resolver.pure(from.syntax,from); b <- resolver.pure(to.syntax,to) if a.name != b.name) yield a.name -> b.name).toList
    }
    val factoryRoles = roles.filter(_.constructorProperties).flatMap { role =>
      require(role.within.nonEmpty && role.target.isEmpty && role.targetOwner.isEmpty && role.names.isEmpty,"Constructor projection takes a namespace, not individual type mappings")
      val pairs = currentTrees.values.toList.flatMap { tree =>
        val classes = tree.collect { case c: Defn.Class if c.mods.exists(_.is[Mod.Case]) => c }
        tree.collect { case m: Defn.Def if m.name.value == "apply" && resolver.owner(m).startsWith(role.within + ".") && m.body.is[Term.New] => m }.flatMap { m =>
          val namespace = resolver.owner(m)
          val init = m.body.asInstanceOf[Term.New].init
          val matching = classes.filter(c => resolver.owner(c) + "." + c.name.value == namespace &&
            Set(c.name.value,namespace,"_root_." + namespace)(init.tpe.syntax))
          if(matching.size != 1 || init.argClauses.size != 1 || matching.head.ctor.paramClauses.size != 1) Nil else {
            val fields = matching.head.ctor.paramClauses.head.values
            val args = init.argClauses.head.values
            val parameters = m.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values)
            if(args.size > fields.size || fields.drop(args.size).exists(_.default.isEmpty)) Nil else args.zip(fields).flatMap {
              case (arg: Term.Name,field) if arg.value == field.name.value =>
                parameters.filter(_.name.value == arg.value).flatMap(p => p.decltpe.toList.flatMap(a => field.decltpe.toList.flatMap(b => forwardingPairs(a,b)))).filter {
                  case(a,b) => a.startsWith(namespace + ".Apply") && b.startsWith(namespace + ".") && !b.startsWith(namespace + ".Apply")
                }
              case _ => Nil
            }
          }
        }
      }.distinct
      require(role.expected < 0 || pairs.size == role.expected,"Constructor projection count differs: expected=" + role.expected + " actual=" + pairs.size)
      pairs.map { case(a,b) => AstRecipe.Consolidation(within = a.substring(0,a.lastIndexOf('.')), names = List(a.split('.').last), target = b, expected = 1) }
    }
    val selected = (roles.filterNot(_.constructorProperties) ++ factoryRoles).flatMap { role =>
      require(role.reuseAcross.isEmpty || (role.reuseAcross.size == 2 && role.reuseAcross.distinct.size == 2 && role.target.nonEmpty && role.names.nonEmpty && role.expected > 0),"Cross-module reuse requires exact named bindings, a positive count and a directed module pair")
      require(role.within.nonEmpty && (role.target.nonEmpty != role.targetOwner.nonEmpty),"Choose one canonical type or projection owner")
      require(role.target.isEmpty || (role.names.nonEmpty && role.target.contains(".")),"Canonical role needs names and a qualified type")
      val candidates = definitions.filter { d =>
        val scope = if(role.targetOwner.nonEmpty) d(5).stripSuffix("." + d(6)) == role.within else d(5).startsWith(role.within + ".")
        scope && (role.names.isEmpty || role.names.contains(d(6))) && d(5) != role.target
      }.flatMap { d =>
        val name = if(role.targetOwner.nonEmpty) role.targetOwner + "." + d(6) else role.target
        val targets = definitions.filter(_(5) == name)
        require(targets.size <= 1,"Canonical definition ambiguous: " + name)
        if(role.target.nonEmpty) require(targets.nonEmpty,"Canonical definition missing: " + name)
        targets.map(target => (d,target))
      }
      require(role.expected < 0 || candidates.size == role.expected,"Consolidation candidate count differs: " + role.target + " expected=" + role.expected + " actual=" + candidates.size)
      candidates.map { case(d,target) =>
        require(d(3) == target(3),"Consolidation cannot cross source-set boundaries")
        require(d(2) == target(2) || role.reuseAcross == List(d(2),target(2)),"Consolidation cannot cross an unapproved module boundary")
        require(d(12) == "member" && target(12) == "member","Local consolidation requires semantic evidence")
        (d,target)
      }
    }
    require(selected.map(_._1.head).distinct.size == selected.size,"Overlapping consolidation roles")
    val sourceNames = selected.map(_._1(5)).toSet
    require(!selected.exists(p => sourceNames(p._2(5))),"Canonical type cannot itself be consolidated")
    val paths = selected.flatMap(p => List(p._1(1),p._2(1))).distinct
    def owner(t: Tree): String = t.parent.toList.flatMap(p => ancestors(p)).reverse.collect {
      case p: Pkg => p.ref.syntax
      case o: Defn.Object => o.name.value
      case c: Defn.Class => c.name.value
      case c: Defn.Trait => c.name.value
      case e: Defn.Enum => e.name.value
    }.mkString(".")
    def ancestors(t: Tree): List[Tree] = t :: t.parent.toList.flatMap(ancestors)
    val trees = paths.map(p => p -> newTrees.getOrElse(p,currentTrees(p))).toMap
    def definition(d: List[String], canonical: Boolean = false): (Defn.Object,Defn.Type,Type.Apply) = {
      val matches = trees(d(1)).collect { case o: Defn.Object if owner(o) + "." + o.name.value == d(5) => o }
      require(matches.size == 1,"Definition does not resolve uniquely in AST: " + d.head)
      val obj = matches.head
      require(!ancestors(obj).tail.exists(t => t.is[Defn.Class] || t.is[Defn.Trait] || t.is[Defn.Def]),"Consolidation owner is not a stable object path: " + d.head)
      require(obj.mods.isEmpty && (obj.templ.stats.isEmpty || (canonical && obj.templ.stats.forall(_.is[Defn.GivenAlias]))) && obj.templ.inits.size == 1,"Companion has behavior or unsupported modifiers: " + d.head)
      val init = obj.templ.inits.head
      require(init.argClauses.isEmpty,"Newtype parent constructor has arguments: " + d.head)
      val parent = init.tpe match {
        case a: Type.Apply if a.tpe.syntax.stripPrefix("_root_.") == "io.github.iltotore.iron.RefinedType" && a.argClause.values.size == 2 => a
        case _ => throw new IllegalArgumentException("Expected qualified Iron RefinedType parent: " + d.head)
      }
      val aliases = obj.parent.toList.flatMap(_.children).collect {
        case t: Defn.Type if t.name.value == obj.name.value && t.body.syntax == obj.name.value + ".T" && t.mods.isEmpty && t.tparamClause.values.isEmpty => t
      }
      require(aliases.size == 1,"Canonical .T alias missing or ambiguous: " + d.head)
      (obj,aliases.head,parent)
    }
    def base(t: Type): String = t.syntax.replace("_root_.","") match {
      case "java.lang.String" => "String"
      case s if s.startsWith("scala.") && Set("Boolean","Byte","Short","Int","Long","Float","Double","Char")(s.stripPrefix("scala.")) => s.stripPrefix("scala.")
      case s => s
    }
    val patches = selected.flatMap { case(d,target) =>
      val (obj,alias,parent) = definition(d)
      val (_,_,canonical) = definition(target,canonical = true)
      require(base(parent.argClause.values.head) == base(canonical.argClause.values.head),"Consolidation base mismatch: " + d.head)
      val constraint = parent.argClause.values(1).syntax.stripPrefix("_root_.")
      require(constraint == "io.github.iltotore.iron.constraint.any.Pure" && constraint == canonical.argClause.values(1).syntax.stripPrefix("_root_."),"Consolidation constraint needs separate construction review: " + d.head)
      val path = "_root_." + target(5)
      val replacementAlias = "type " + obj.name.value + " = " + path
      val replacementObject = "val " + obj.name.value + ": " + path + ".type = " + path
      val values = obj.parent.toList.flatMap(_.children).collect {
        case v: Defn.Val if v.pats.exists { case Pat.Var(n) => n.value == obj.name.value; case _ => false } => v
      }
      require(values.size <= 1,"Ambiguous existing value beside newtype: " + d.head)
      val references = trees(d(1)).collect { case a: Term.Apply if a.fun.syntax.stripPrefix("_root_.") == d(5) => a.fun }
      if(values.nonEmpty) require(values.head.decltpe.exists(_.collect { case t: Type if t.syntax.stripPrefix("_root_.") == d(5) => true }.nonEmpty) &&
        references.forall(t => values.head.rhs.pos.start <= t.pos.start && t.pos.end <= values.head.rhs.pos.end),"Constructor/value collision needs semantic review: " + d.head)
      val changes: List[(Tree,String)] = List(alias -> replacementAlias, obj -> (if(values.isEmpty) replacementObject else "")) ++
        (if(values.isEmpty) Nil else references.map(_ -> path))
      changes.map { case(tree,next) =>
        evidence += List(d(1),(tree.pos.startLine + 1).toString,"CONSOLIDATE " + d.head + " " + tree.structure,if(next.isEmpty) "remove conflicting empty companion; preserve existing value" else next.parse[Stat].get.structure)
        val source = Files.readString(Paths.get(d(1)))
        val start = if(next.isEmpty) source.lastIndexOf('\n',tree.pos.start - 1) + 1 else tree.pos.start
        val end = if(next.isEmpty) trees(d(1)).tokens.find(t => t.pos.start == tree.pos.end && t.is[Token.LF]).map(_.pos.end).getOrElse(tree.pos.end) else tree.pos.end
        require(next.nonEmpty || source.substring(start,tree.pos.start).forall(_ == ' '),"Companion removal is not a complete statement")
        d(1) -> (start,end,next)
      }
    }
    val primitives = roles.filter(_.primitiveEitherLeft).flatMap { role =>
      val target = definitions.find(_(5) == role.target).get
      val (_,_,parent) = definition(target,canonical = true)
      require(base(parent.argClause.values.head) == "String" && parent.argClause.values(1).syntax.stripPrefix("_root_.") == "io.github.iltotore.iron.constraint.any.Pure","Expected Pure error-message type")
      val sourceRoot = target(1).split("/src/").head + "/src/main/"
      frozen.keys.toList.sorted.filter(p => p.startsWith(sourceRoot) && p.endsWith(".scala")).flatMap { path =>
        val tree = Files.readString(Paths.get(path)).parse[Source].get
        val shadowed = tree.collect { case t: Defn.Type if t.name.value == "String" => true; case t: Type.Param if t.name.value == "String" => true }.nonEmpty
        tree.collect {
          case a: Type.Apply if Set("Either","scala.Either","scala.util.Either")(a.tpe.syntax) && a.argClause.values.size == 2 && a.argClause.values.head.syntax == "String" &&
            (owner(a) == role.within || owner(a).startsWith(role.within + ".")) && !shadowed =>
            val t = a.argClause.values.head
            evidence += List(path,(t.pos.startLine + 1).toString,"PRIMITIVE_ROLE Either.Left " + t.structure,role.target)
            path -> ((t.pos.start,t.pos.end,"_root_." + role.target))
        }
      }
    }
    (patches ++ primitives).groupBy(_._1).map { case(p,rs) => p -> rs.map(_._2) }
  }
}

class NewtypeIndex(histories: List[String], previous: String) {
  private val definitions = scala.collection.mutable.ListBuffer.empty[List[String]]
  private val references = scala.collection.mutable.ListBuffer.empty[List[String]]
  private val definitionOrdinals = scala.collection.mutable.Map.empty[String,Int]
  def entries: List[List[String]] = definitions.toList
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def owner(t: Tree): String = parents(t).reverse.collect {
    case p: Pkg => p.ref.syntax
    case o: Defn.Object => o.name.value
    case c: Defn.Class => c.name.value
    case c: Defn.Trait => c.name.value
    case e: Defn.Enum => e.name.value
    case d: Defn.Def => d.name.value
  }.mkString(".")
  private def canonical(s: String): String = s.replace("_root_.","").replace("java.lang.String","String").replace("scala.","")
  def inspect(tree: Source,path: String,module: String,scope: String): Unit = {
    def add(t: Tree,name: String,kind: String,base: String,rule: String,alias: String): Unit = {
      val full = owner(t) + "." + name
      val key = path + "::" + full
      val ordinal = definitionOrdinals.getOrElse(key,0); definitionOrdinals(key) = ordinal + 1
      definitions += List(key + "#" + ordinal,path,module,scope,(t.pos.startLine + 1).toString,full,name,kind,base,rule,
        canonical(base),rule.stripPrefix("_root_."),if(parents(t).exists(_.is[Defn.Def])) "local" else "member",alias,t.structure)
    }
    tree.collect {
      case o: Defn.Object => o.templ.inits.foreach { init => init.tpe match {
        case a: Type.Apply if Set("RefinedType","RefinedSubtype")(a.tpe.syntax.split('.').last) && a.argClause.values.size == 2 =>
          val linked = o.parent.toList.flatMap(_.children).exists {
            case d: Defn.Type => d.name.value == o.name.value && d.body.syntax == o.name.value + ".T"
            case _ => false
          }
          add(o,o.name.value,"Iron-parent-shape",a.argClause.values.head.syntax,a.argClause.values(1).syntax,if(linked) "alias-present" else "alias-unresolved")
        case _ => ()
      }}
      case t: Defn.Type if t.mods.exists(_.is[Mod.Opaque]) => add(t,t.name.value,"opaque",t.body.syntax,"opaque-bound-unresolved","opaque")
    }
  }
  def use(t: Type,path: String,module: String,scope: String,line: String,context: String,kind: String): Unit = t.collect {
    case s: Type.Select if !s.syntax.endsWith(".T") => references += List(path,module,scope,line,context,kind,s.syntax.stripPrefix("_root_."))
    case n: Type.Name if !n.parent.exists(_.is[Type.Select]) => references += List(path,module,scope,line,context,kind,n.value)
  }
  def finish(out: java.nio.file.Path, expected: List[String], expectedGroups: Map[String,Int]): Unit = {
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    def write(name: String,header: String,rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(escape).mkString("\t"))).asJava)
    val ds = definitions.toList.sortBy(r => (r(1),r(5)))
    require(ds.map(_.head).distinct.size == ds.size,"Duplicate newtype index identity")
    if(expected.nonEmpty) require(ds.map(_(5)).sorted == expected.sorted,"Newtype fixture definition mismatch")
    val history = histories.flatMap { p =>
      val file = Paths.get(p).getParent.resolve("shapes.tsv")
      if(!Files.exists(file)) Nil else Files.readAllLines(file).asScala.drop(1).flatMap { line =>
        val r = line.split("\t",-1)
        if(r.length != 4 || !r(2).contains("#") || r(2).startsWith("CORRECTION") || r(2).startsWith("UNRESOLVED") || !r(3).matches("[A-Za-z_][A-Za-z0-9_.]*")) Nil
        else {
          val parts = r(2).split("#",2)
          val target = if(r(3).startsWith("com.")) r(3) else parts.head + "." + r(3).split('.').last
          List(List(r(0),target,parts(1).takeWhile(_ != '/'),file.toString))
        }
      }.toList
    }.distinct
    val historyByOwner = history.groupBy(r => (r.head,r(1)))
    val historyByName = history.groupBy(r => (r.head,r(1).split('.').last))
    val origin = ds.flatMap { d =>
      val exact = historyByOwner.getOrElse((d(1),d(5)),Nil)
      val fallback = historyByName.getOrElse((d(1),d(6)),Nil)
      val selected = if(exact.nonEmpty) exact else if(fallback.map(_(1)).distinct.size == 1) fallback else Nil
      selected.map(r => List(d.head,r(2),r(3),if(exact.nonEmpty) "owner-match" else "unique-source-name; review"))
    }
    val byFull = ds.groupBy(_(5))
    val byName = ds.groupBy(_(6))
    val uses = references.toList.distinct.flatMap { r =>
      val raw = r(6)
      if(!byName.contains(raw.split('.').last)) Nil else {
        val prefixes = r(4).split('.').toList.inits.map(_.mkString(".")).toList
        val matches = (raw :: prefixes.map(p => if(p.isEmpty) raw else p + "." + raw)).distinct.iterator.map(k => byFull.getOrElse(k,Nil)).find(_.nonEmpty).getOrElse(Nil)
        if(matches.size == 1) List(r ++ List(matches.head.head,if(raw == matches.head(5)) "fully-qualified-AST-match" else "lexical-AST-match; imports-unverified"))
        else List(r ++ List("UNRESOLVED","import-or-owner-ambiguity"))
      }
    }
    val byOrigin = origin.groupBy(_.head)
    val members = ds.filter(_(3) == "production").flatMap { d =>
      val tokens = byOrigin.getOrElse(d.head,Nil).map(_(1).split('.').last).filter(_.nonEmpty).distinct
      (if(tokens.nonEmpty) tokens else List(d(6))).map { token =>
        val purpose = token.toLowerCase(java.util.Locale.ROOT)
        (purpose,d(10),d)
      }
    }.groupBy(x => (x._1,x._2)).toList.filter(_._2.map(_._3.head).distinct.size > 1)
    val generic = Set("id","name","value","key","result","wire","label","text","kind","n","s","x","y")
    val priority = Set("slug","citekey","title","year","seconds","bytes","offset","limit","pagesize","sha256","doi","budgetusd","tokens","lat","lon")
    val membership = scala.collection.mutable.ListBuffer.empty[List[String]]
    val groups = members.map { case((purpose,base),rs) =>
      val selected = rs.map(_._3).groupBy(_.head).values.map(_.head).toList.sortBy(_.head)
      val key = purpose + "|" + base
      val id = java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).take(10).map(b => f"${b & 255}%02x").mkString
      selected.foreach(d => membership += List(id,d.head,purpose,"candidate-only; do not merge automatically"))
      val constraints = selected.map(_(11)).distinct.sorted
      val modules = selected.map(_(2)).distinct.sorted
      val score = (if(priority(purpose)) 100 else if(generic(purpose)) 0 else 20) + math.min(selected.size,50)
      List(id,purpose,base,selected.size.toString,constraints.size.toString,modules.mkString(";"),constraints.mkString(";"),
        if(constraints.size > 1) "constraint-syntax-diff; resolution-and-construction-review" else "same-syntax; semantic-identity-unverified",
        if(modules.size > 1) "cross-module-and-layer-review" else "owner-purpose-review",score.toString)
    }.sortBy(r => (-r(9).toInt,r(1),r(2)))
    expectedGroups.foreach { case(p,n) => require(membership.filter(_(2) == p).map(_(1)).distinct.size == n,"Overlap fixture membership differs: " + p) }
    write("newtypes.tsv","id\tpath\tmodule\tscope\tline\tlexical-name\tname\tkind\tbase\tconstraint\tbase-bucket\tconstraint-syntax\tlocality\talias\tast",ds)
    if(previous.nonEmpty && Files.exists(Paths.get(previous,"newtypes.tsv"))) {
      val before = Files.readAllLines(Paths.get(previous,"newtypes.tsv")).asScala.drop(1).map(_.takeWhile(_ != '\t')).toSet
      val after = ds.map(_.head).toSet
      write("newtype-changes.tsv","definition\tstatus\tprevious-index",
        (before -- after).toList.sorted.map(id => List(id,"removed-newtype-shape-unverified",previous)) ++
        (after -- before).toList.sorted.map(id => List(id,"introduced-newtype-shape",previous)))
    }
    write("newtype-origins.tsv","definition\tdeclaration-origin\tevidence\tmatching",origin)
    write("newtype-uses.tsv","path\tmodule\tscope\tline\tcontext\tkind\tsyntax\tdefinition\tmatching",uses)
    write("overlap-groups.tsv","group\tpurpose-token\tbase-bucket\tdefinitions\tconstraint-variants\tmodules\tconstraints\tconstraint-review\tboundary-review\tpriority",groups)
    write("overlap-members.tsv","group\tdefinition\tpurpose-token\tstatus",membership.toList)
    write("newtype-counts.tsv","scope\tmodule\tkind\tdefinitions",ds.groupBy(r => (r(3),r(2),r(7))).toList.sortBy(_._1).map { case((s,m,k),rs) => List(s,m,k,rs.size.toString) })
    val note = s"${ds.size} newtype-shaped definitions (${ds.count(_(3) == "production")} production, ${ds.count(_(3) == "test")} test); ${groups.size} production overlap candidate groups; ${uses.count(_(7) == "UNRESOLVED")} unresolved candidate references.\nGrouping is a review queue, not semantic equivalence. Raw syntax constraints, owner meaning and module isolation must be reviewed. Imports, aliases, inherited newtypes and external symbols are not resolved. Reference counts cover measured declaration types only.\n"
    Files.writeString(out.resolve("newtype-index.txt"),note)
    println(note)
  }
}

object TypeMeasurement {
  def run(sources: Map[String,String], out: java.nio.file.Path, confirmed: List[String], expected: List[String], previous: String, indexTypes: Boolean, histories: List[String], expectedNewtypes: List[String], expectedOverlap: Map[String,Int]): Unit = {
    import scala.jdk.CollectionConverters._
    implicit val dialect: Dialect = dialects.Scala3
    val primitives = Set("String","Boolean","Byte","Short","Int","Long","Float","Double","Char")
    val positions = scala.collection.mutable.ListBuffer.empty[List[String]]
    val leaves = scala.collection.mutable.ListBuffer.empty[List[String]]
    val contexts = scala.collection.mutable.Map.empty[String,List[String]]
    val index = if(indexTypes) Some(new NewtypeIndex(histories,previous)) else None
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def owner(t: Tree): String = parents(t).reverse.collect {
      case p: Pkg => p.ref.syntax
      case c: Defn.Class => c.name.value
      case c: Defn.Trait => c.name.value
      case c: Defn.Object => c.name.value
      case c: Defn.Enum => c.name.value
      case d: Defn.Def if !(d.name eq t) => d.name.value
      case d: Decl.Def if !(d.name eq t) => d.name.value
    }.mkString(".")
    sources.toList.sortBy(_._1).foreach { case(path,_) =>
      val source = Files.readString(Paths.get(path))
      val tree = source.parse[Source].get
      val scope = if(path.contains("/src/test/")) "test" else "production"
      val module = path.stripPrefix("modules/").split("/src/").head
      index.foreach(_.inspect(tree,path,module,scope))
      val serial = scala.collection.mutable.Map.empty[String,Int]
      def position(n: Name, kind: String, declared: Option[Type]): Unit = {
        val o = owner(n)
        val key = o + "." + n.value + ":" + kind
        val ordinal = serial.getOrElse(key,0); serial(key) = ordinal + 1
        val id = path + "::" + key + "#" + ordinal
        val methods = parents(n).collect { case d: Defn.Def => d }
        val local = kind == "lambda-parameter" || methods.exists(d => !(d.name eq n) &&
          !(kind == "method-parameter" && methods.headOption.contains(d)))
        positions += List(id,path,module,scope,if(local) "local" else "member",(n.pos.startLine + 1).toString,kind,o,n.value,declared.fold("<inferred>")(_.syntax),declared.fold("")(_.structure))
        val ctx = parents(n)
        val mods = ctx.flatMap {
          case d: Defn.Def => d.mods
          case d: Defn.Val => d.mods
          case d: Defn.Var => d.mods
          case d: Defn.Class => d.mods
          case d: Defn.Trait => d.mods
          case d: Defn.Object => d.mods
          case _ => Nil
        }
        val native = mods.exists { case a: Mod.Annot => a.init.tpe.syntax.endsWith("native"); case _ => false } || ctx.exists {
          case d: Defn.Def => d.body match { case Term.Select(_,Term.Name("native")) => true; case _ => false }
          case _ => false
        }
        val category = if(native) "native-contract-review" else if(mods.exists(_.is[Mod.Override])) "override-contract-review"
          else if(kind == "lambda-parameter") "lambda-propagation-review"
          else if(kind == "alias") "alias-signature-review"
          else if(declared.exists(_.collect { case _: Type.ByName => true; case _: Type.Repeated => true; case _: Type.ApplyInfix => true }.nonEmpty)) "wrapper-shape-review"
          else if(declared.exists(_.collect { case a: Type.Apply if a.tpe.syntax.endsWith("Array") => true }.nonEmpty)) "buffer-or-native-array-review"
          else "declaration-owner-or-name-review"
        val treatment = if(Set("wrapper-shape-review","alias-signature-review")(category)) "bulk-rewrite-candidate"
          else if(category == "lambda-propagation-review") "type-propagation-candidate" else "targeted-or-design-review"
        contexts(id) = List(category,treatment,"candidate; no accepted exception inferred")
        declared.foreach(t => index.foreach(_.use(t,path,module,scope,(n.pos.startLine + 1).toString,o,kind)))
        def emit(route: String,status: String,detail: String,t: Option[Type],callback: Boolean): Unit = {
          val selector = key + "|" + route
          leaves += List(id,route,status,detail,t.fold("")(_.structure),if(callback) "callback" else kind,
            if(status == "primitive" && confirmed.contains(selector)) "confirmed" else if(status == "primitive") "candidate" else "unresolved",selector)
        }
        def inspect(t: Type, route: String, callback: Boolean = false): Unit = t match {
          case Type.Name(n) if primitives(n) => emit(route,"primitive",n,Some(t),callback)
          case Type.Select(Term.Name("scala"),Type.Name(n)) if primitives(n) => emit(route,"primitive",n,Some(t),callback)
          case Type.Select(Term.Select(Term.Name("java"),Term.Name("lang")),Type.Name("String")) => emit(route,"primitive","String",Some(t),callback)
          case a: Type.Apply => a.argClause.values.zipWithIndex.foreach { case(v,i) => inspect(v,route + "/argument[" + i + "]",callback) }
          case f: Type.Function =>
            f.paramClause.values.zipWithIndex.foreach { case(v,i) => inspect(v,route + "/function-parameter[" + i + "]",true) }
            inspect(f.res,route + "/function-result",true)
          case f: Type.ContextFunction =>
            f.paramClause.values.zipWithIndex.foreach { case(v,i) => inspect(v,route + "/context-parameter[" + i + "]",true) }
            inspect(f.res,route + "/context-result",true)
          case t: Type.Tuple => t.args.zipWithIndex.foreach { case(v,i) => inspect(v,route + "/tuple[" + i + "]",callback) }
          case t: Type.ByName => inspect(t.tpe,route + "/by-name",callback)
          case t: Type.Repeated => inspect(t.tpe,route + "/repeated",callback)
          case t: Type.ApplyInfix => inspect(t.lhs,route + "/left",callback); inspect(t.rhs,route + "/right",callback)
          case Type.Name(n) if Set("Unit","Nothing","Any","AnyRef","Null")(n) => ()
          case _ => emit(route,"unresolved",t.syntax,Some(t),callback)
        }
        declared match {
          case Some(t) => inspect(t,"type")
          case None => emit("type","inferred-unresolved","No current semantic type available",None,false)
        }
      }
      tree.collect {
        case p: Term.Param =>
          val context = parents(p)
          val kind = context.collectFirst {
            case _: Term.Function => "lambda-parameter"
            case _: Term.ContextFunction => "lambda-parameter"
            case _: Ctor.Primary => "constructor-parameter"
            case _: Defn.Def => "method-parameter"
            case _: Decl.Def => "method-parameter"
          }.getOrElse("parameter")
          position(p.name,kind,p.decltpe)
        case d: Defn.Def => position(d.name,"method-result",d.decltpe)
        case d: Decl.Def => position(d.name,"method-result",Some(d.decltpe))
        case v: Defn.Val => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"value",v.decltpe))
        case v: Defn.Var => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"variable",v.decltpe))
        case v: Decl.Val => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"value",Some(v.decltpe)))
        case v: Decl.Var => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"variable",Some(v.decltpe)))
        case t: Defn.Type if !t.mods.exists(_.is[Mod.Opaque]) => position(t.name,"alias",Some(t.body))
      }
    }
    val byId = positions.map(p => p.head -> p).toMap
    require(byId.size == positions.size,"Duplicate measurement identities")
    val observed = leaves.filter(_(2) == "primitive").map(_(7)).toSet
    require(confirmed.forall(observed),"Confirmed measurement selector missing: " + confirmed.filterNot(observed).mkString(", "))
    if(expected.nonEmpty) require(leaves.filter(_(2) == "primitive").map(_(7)).toList.sorted == expected.sorted,"Exact primitive selector fixture mismatch")
    def esc(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\r","\\r").replace("\n","\\n")
    def write(name: String,header: String,rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(esc).mkString("\t"))).asJava)
    write("positions.tsv","id\tpath\tmodule\tscope\tlocality\tline\tkind\towner\tname\tdeclared\tast",positions.toList)
    write("leaves.tsv","position\troute\tstatus\tdetail\tast\tfamily\tclassification\tselector",leaves.toList)
    val residual = leaves.toList.filter(l => l(2) == "primitive" && byId(l.head)(3) == "production").map { l =>
      val p = byId(l.head)
      List(l.head,l(1),p(1),p(5),p(2),p(6),p(7),p(8),l(3)) ++ contexts(l.head)
    }
    write("residual-treatment.tsv","position\troute\tpath\tline\tmodule\tkind\towner\tname\tprimitive\tcategory\tproposed-treatment\tstatus",residual)
    write("residual-treatment-counts.tsv","category\tproposed-treatment\tleaves",residual.groupBy(r => (r(9),r(10))).toList.sortBy(_._1).map { case((c,t),rs) => List(c,t,rs.size.toString) })
    write("residual-treatment-samples.tsv","position\troute\tpath\tline\tmodule\tkind\towner\tname\tprimitive\tcategory\tproposed-treatment\tstatus",residual.groupBy(_(9)).toList.sortBy(_._1).flatMap { case(_,rs) => rs.groupBy(_(4)).toList.sortBy(_._1).take(2).map(_._2.head) })
    index.foreach(_.finish(out,expectedNewtypes,expectedOverlap))
    write("sources.tsv","path\tsha256",sources.toList.sorted.map { case(p,h) => List(p,h) })
    if(sources.keys.forall(_.startsWith("modules/"))) {
      val stream = Files.walk(Paths.get("modules"))
      val current = try stream.iterator.asScala.filter(Files.isRegularFile(_)).map(_.toString).filter(p =>
        p.endsWith(".scala") && (p.contains("/src/main/") || p.contains("/src/test/")) &&
          !p.contains("/target/") && !p.contains("/.scala-build/")).toSet finally stream.close()
      write("coverage.tsv","path\tstatus",(current ++ sources.keySet).toList.sorted.map(p => List(p,
        if(!current(p)) "manifest-only" else if(!sources.contains(p)) "UNMEASURED" else "measured")))
      require(current == sources.keySet,"Current source coverage differs; see coverage.tsv (no completion claim)")
    }
    val groups = leaves.toList.groupBy(l => (byId(l.head)(3),byId(l.head)(2),byId(l.head)(4),l(5),l(2),l(6))).toList.sortBy(_._1)
    write("counts.tsv","scope\tmodule\tlocality\tfamily\tstatus\tclassification\tpositions\tleaves",groups.map { case((s,m,o,f,t,c),ls) => List(s,m,o,f,t,c,ls.map(_.head).distinct.size.toString,ls.size.toString) })
    val summary = leaves.toList.groupBy(l => (byId(l.head)(3),byId(l.head)(4),l(5),l(2),l(6))).toList.sortBy(_._1)
    write("totals.tsv","scope\tlocality\tfamily\tstatus\tclassification\tpositions\tleaves",summary.map { case((s,o,f,t,c),ls) => List(s,o,f,t,c,ls.map(_.head).distinct.size.toString,ls.size.toString) })
    if(previous.nonEmpty) {
      val before = Files.readAllLines(Paths.get(previous,"leaves.tsv")).asScala.drop(1).map(_.split("\t",-1)).filter(_(2) == "primitive").map(r => r(0) + "|" + r(1)).toSet
      val after = leaves.filter(_(2) == "primitive").map(r => esc(r.head) + "|" + esc(r(1))).toSet
      val delta = before.diff(after).toList.sorted.map(id => List("raw-primitive-eliminated-unverified",id)) ++ after.diff(before).toList.sorted.map(id => List("new-primitive",id))
      write("changes.tsv","change\tstable-position-and-route",delta)
      println(s"Stable-ID delta: ${before.diff(after).size} raw leaves eliminated; ${after.diff(before).size} new primitive leaves")
    }
    Files.writeString(out.resolve("measurement.txt"),s"${sources.size} files; ${positions.size} positions; ${leaves.count(_(2) == "primitive")} explicit primitive leaves; ${leaves.count(_(6) == "confirmed")} confirmed occurrences.\nAST-only: inference, aliases, named references and primitive shadowing require semantic review. Candidates are not confirmed violations; no accepted exceptions inferred. IDs omit line numbers; same-name overloads use source-order ordinals. Historical semantic inventory remains unchanged.\n")
    println(Files.readString(out.resolve("measurement.txt")))
  }
}

object BufferSignatureRepair {
  def patches(tree: Source, path: String, targets: List[String], external: Map[String,List[String]], evidence: scala.collection.mutable.ListBuffer[List[String]]): List[(Int,Int,String)] = {
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def qualified(o: Defn.Object): String = (parents(o).reverse :+ o).collect {
      case p: Pkg => p.ref.syntax
      case o: Defn.Object => o.name.value
      case c: Defn.Class => c.name.value
      case c: Defn.Trait => c.name.value
      case c: Defn.Enum => c.name.value
    }.mkString(".")
    targets.flatMap { target =>
      val candidates = tree.collect { case o: Defn.Object if qualified(o) == target => o }
      require(candidates.size == 1,"Historical buffer newtype owner changed: " + target)
      val o = candidates.head
      require(o.templ.inits.size == 1 && o.templ.stats.isEmpty,"Historical buffer newtype changed: " + target)
      val parent = o.templ.inits.head.tpe.asInstanceOf[Type.Apply]
      require(parent.tpe.syntax.endsWith("RefinedType") && parent.argClause.values.size == 2 && parent.argClause.values(1).syntax.endsWith(".Pure"))
      val base = parent.argClause.values.head
      if(base.syntax == "_root_.scala.Array[_root_.scala.Byte]") Nil
      else {
        require(base.syntax == "_root_.scala.Byte","Historical buffer base differs: " + target)
        val refs = tree.collect { case t: Type.Select if t.syntax == target => t }
        val arrays = tree.collect { case a: Type.Apply if Set("Array","scala.Array","_root_.scala.Array")(a.tpe.syntax) &&
          a.argClause.values.size == 1 && a.argClause.values.head.syntax == target => a }
        if(refs.nonEmpty && refs.size == arrays.size && !external.contains(target)) {
          val edits = arrays.map { a =>
            evidence += List(path,(a.pos.startLine + 1).toString,"CORRECTION " + target + " " + a.structure,target)
            (a.pos.start,a.pos.end,target)
          }
          edits :+ ((base.pos.start,base.pos.end,"_root_.scala.Array[_root_.scala.Byte]"))
        } else {
          evidence += List(path,(o.pos.startLine + 1).toString,"UNRESOLVED buffer correction " + target,"Non-array, mixed, absent or cross-file references; primitive newtype preserved. " + external.getOrElse(target,Nil).mkString(","))
          Nil
        }
      }
    }.distinct
  }
}

object MemberDefinitions {
  def patches(tree: Source, source: String, path: String, reuse: Map[String,String], names: Map[String,String], wholeBuffers: Boolean, localMembers: Boolean,
      evidence: scala.collection.mutable.ListBuffer[List[String]]): List[(Int,Int,String)] = {
    val result = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
    val additions = scala.collection.mutable.LinkedHashMap.empty[Tree,scala.collection.mutable.LinkedHashMap[String,String]]
    val localDeclarations = scala.collection.mutable.Map.empty[(Tree,String),Int]
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def indent(t: Tree): String = source.substring(source.lastIndexOf('\n',t.pos.start - 1) + 1,t.pos.start).takeWhile(_ == ' ')
    def upper(s: String): String = if(s.isEmpty) "Anonymous" else s.head.toUpper + s.tail
    def ownerName(t: Tree): String = (parents(t).reverse :+ t).collect {
      case p: Pkg => p.ref.syntax
      case o: Defn.Object => o.name.value
      case c: Defn.Class => c.name.value
      case c: Defn.Trait => c.name.value
      case c: Defn.Enum => c.name.value
    }.mkString(".")
    def stats(t: Tree): List[Stat] = t match {
      case o: Defn.Object => o.templ.stats
      case _ => Nil
    }
    def leaves(t: Type,suffix: String = ""): List[(Type,String,String)] = t match {
      case Type.Name(n) if Set("String","Int","Long","Double","Boolean","Float","Short","Byte","Char")(n) => List((t,n,suffix))
      case a: Type.Apply if wholeBuffers && Set("Array","scala.Array","_root_.scala.Array")(a.tpe.syntax) &&
        a.argClause.values.size == 1 && Set("Byte","Int")(a.argClause.values.head.syntax) => List((a,"Array[" + a.argClause.values.head.syntax + "]",suffix))
      case a: Type.Apply => a.argClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + (if(a.argClause.values.size == 1) "" else "Part" + (i + 1))) }
      case f: Type.Function => f.paramClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "Arg" + (i + 1)) } ++ leaves(f.res,suffix + "Result")
      case f: Type.ContextFunction => f.paramClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "ContextArg" + (i + 1)) } ++ leaves(f.res,suffix + "Result")
      case t: Type.Tuple => t.args.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "Part" + (i + 1)) }
      case _ => Nil
    }
    def rewrite(declaration: Tree, keyName: String, prefix: String, t: Type, blocked: Boolean): Unit = {
      val context = parents(declaration)
      val enclosing = context.collectFirst { case o: Defn.Object => o; case c: Defn.Class => c; case c: Defn.Trait => c; case c: Defn.Enum => c }
      enclosing.foreach { entity =>
        val owner = ownerName(entity)
        val enclosingMethods = context.takeWhile(_ ne entity).reverse.collect { case d: Defn.Def => d.name.value }
        val local = enclosingMethods.nonEmpty
        val localOwner = parents(entity).exists(t => t.is[Defn.Def] || t.is[Term.Block])
        val targetOwner: Tree = entity match {
          case o: Defn.Object => o
          case _ => entity.parent.toList.flatMap(_.children).collectFirst {
            case o: Defn.Object if o.name.value == owner.split('.').last => o
          }.getOrElse(entity)
        }
        val occupied = stats(targetOwner).flatMap {
          case d: Defn.Type => List(d.name.value)
          case d: Defn.Object => List(d.name.value)
          case d: Defn.Class => List(d.name.value)
          case d: Defn.Trait => List(d.name.value)
          case d: Defn.Enum => List(d.name.value)
          case _ => Nil
        }.toSet
        leaves(t).foreach { case(leaf,base,suffix) =>
          val scopedKey = (enclosingMethods :+ keyName).mkString(".")
          val key = owner + "." + scopedKey + (if(suffix.isEmpty) "" else "." + suffix)
          val name = names.getOrElse(key,enclosingMethods.map(upper).mkString + prefix + suffix)
          val mappings = reuse.toList.collect { case(pattern,value) if key.matches(pattern.split("\\*",-1).map(java.util.regex.Pattern.quote).mkString(".*")) => value }.distinct
          require(mappings.size <= 1,"Conflicting member concept mappings: " + key)
          val declared = additions.getOrElseUpdate(targetOwner,scala.collection.mutable.LinkedHashMap.empty[String,String])
          val shadowed = tree.collect { case d: Defn.Type if d.name.value == base => d }.nonEmpty
          val localCollision = local && localDeclarations.get((targetOwner,name)).exists(_ != declaration.pos.start)
          val packed = !base.startsWith("Array[") && (leaf :: parents(leaf).takeWhile(x => x.pos.start >= t.pos.start && x.pos.end <= t.pos.end)).exists {
            case a: Type.Apply => Set("Array","scala.Array","_root_.scala.Array","js.Array")(a.tpe.syntax)
            case _ => false
          }
          val empty = targetOwner.is[Defn.Object] && stats(targetOwner).isEmpty
          if(blocked || (local && !localMembers) || localOwner || localCollision || shadowed || packed || empty || !name.matches("[A-Z][A-Za-z0-9_]*") ||
            (mappings.isEmpty && (occupied(name) || declared.get(name).exists(_ != base)))) {
            val reasons = List(if(blocked) "override-or-native-contract" else "",if(local && !localMembers) "local" else "",if(localOwner) "local-owner-requires-local-companion" else "",if(localCollision) "local-declaration-name-conflict" else "",if(shadowed) "primitive-name-shadowing" else "",if(packed) "whole-buffer-modeling-required" else "",if(empty) "empty-companion" else "").filter(_.nonEmpty)
            evidence += List(path,(leaf.pos.startLine + 1).toString,"UNRESOLVED member " + key,leaf.structure + " " + (if(reasons.isEmpty) "name-or-base-conflict" else reasons.mkString(",")))
          } else {
            val replacement = mappings.headOption.getOrElse(owner + "." + name)
            if(mappings.isEmpty) declared(name) = base
            if(local) localDeclarations((targetOwner,name)) = declaration.pos.start
            result += ((leaf.pos.start,leaf.pos.end,replacement))
            evidence += List(path,(leaf.pos.startLine + 1).toString,owner + "#" + scopedKey + "/" + suffix + " " + leaf.structure,replacement)
          }
        }
      }
    }
    tree.collect {
      case d: Defn.Def =>
        val blocked = d.mods.exists(_.is[Mod.Override]) || (d.body match { case Term.Select(_,Term.Name("native")) => true; case _ => false })
        d.decltpe.foreach(rewrite(d,d.name.value,upper(d.name.value) + "Result",_,blocked))
        d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).foreach(p => p.decltpe.foreach(rewrite(d,d.name.value + "." + p.name.value,upper(d.name.value) + upper(p.name.value),_,blocked)))
      case d: Decl.Def =>
        rewrite(d,d.name.value,upper(d.name.value) + "Result",d.decltpe,false)
        d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).foreach(p => p.decltpe.foreach(rewrite(d,d.name.value + "." + p.name.value,upper(d.name.value) + upper(p.name.value),_,false)))
      case v: Defn.Val => v.pats.collect { case Pat.Var(n) => n }.foreach(n => v.decltpe.foreach(rewrite(v,n.value,upper(n.value),_,false)))
      case v: Defn.Var => v.pats.collect { case Pat.Var(n) => n }.foreach(n => v.decltpe.foreach(rewrite(v,n.value,upper(n.value),_,false)))
    }
    additions.foreach { case(target,declared) => if(declared.nonEmpty) {
      val own = ownerName(target).split('.').last
      val pad = target match { case o: Defn.Object if o.templ.stats.nonEmpty => indent(o.templ.stats.head); case _ => indent(target) + "  " }
      val definitions = declared.toList.flatMap { case(n,b) => List("type " + n + " = " + n + ".T",
        "object " + n + " extends _root_.io.github.iltotore.iron.RefinedType[" + (if(b == "String") "_root_.java.lang.String" else if(b.startsWith("Array[")) "_root_.scala.Array[_root_.scala." + b.stripPrefix("Array[") else "_root_.scala." + b) + ", _root_.io.github.iltotore.iron.constraint.any.Pure]") }.mkString("\n" + pad)
      target match {
        case o: Defn.Object if o.templ.stats.nonEmpty => val at = o.templ.stats.head.pos.start; result += ((at,at,definitions + "\n\n" + pad))
        case _: Defn.Object => sys.error("Empty companion requires explicit treatment: " + own)
        case _ => result += ((target.pos.end,target.pos.end,"\n\n" + indent(target) + "object " + own + ":\n" + pad + definitions))
      }
    }}
    result.toList
  }
}

object ModelDefinitions {
  def patches(tree: Source, source: String, path: String, reuse: Map[String,String], names: Map[String,String], defer: Map[String,String], buffers: Map[String,String], localModels: List[String], otherConstructors: Boolean,
      evidence: scala.collection.mutable.ListBuffer[List[String]]): List[(Int,Int,String)] = {
    val primitive = Set("String", "Boolean", "Byte", "Short", "Int", "Long", "Float", "Double", "Char")
    val result = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def indent(t: Tree): String = source.substring(source.lastIndexOf('\n',t.pos.start - 1) + 1,t.pos.start).takeWhile(_ == ' ')
    def upper(s: String): String = if(s.isEmpty) "Anonymous" else s.head.toUpper + s.tail
    def qualified(b: String): String = if(b == "String") "_root_.java.lang.String" else "_root_.scala." + b
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
      case f: Type.Function => f.paramClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "Arg" + (i + 1)) } ++ leaves(f.res,suffix + "Result")
      case f: Type.ContextFunction => f.paramClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "ContextArg" + (i + 1)) } ++ leaves(f.res,suffix + "Result")
      case _ => Nil
    })
    tree.collect {
      case c: Defn.Class if c.mods.exists(_.is[Mod.Case]) || otherConstructors => (c: Tree,c.name,c.ctor)
      case e: Defn.Enum if otherConstructors => (e: Tree,e.name,e.ctor)
      case e: Defn.EnumCase if otherConstructors => (e: Tree,e.name,e.ctor)
    }.foreach { case(c,className,ctor) =>
      val ancestry = parents(c)
      val owner = (ancestry.reverse.collect {
        case p: Pkg => p.ref.syntax
        case o: Defn.Object => o.name.value
        case d: Defn.Class => d.name.value
        case d: Defn.Trait => d.name.value
        case d: Defn.Enum => d.name.value
      } :+ className.value).mkString(".")
      val enclosingEnum = if(c.is[Defn.EnumCase]) ancestry.collectFirst { case e: Defn.Enum => e } else None
      val referenceOwner = enclosingEnum.map(_.name.value + ".").getOrElse("") + className.value
      val enumCompanion = enclosingEnum.flatMap(e => e.parent.toList.flatMap(_.children).collectFirst { case o: Defn.Object if o.name.value == e.name.value => o })
      val siblings = if(enclosingEnum.nonEmpty) enumCompanion.toList.flatMap(_.templ.stats) else c.parent.toList.flatMap(_.children)
      val companion = siblings.collectFirst { case o: Defn.Object if o.name.value == className.value => o }
      val declarations = scala.collection.mutable.LinkedHashMap.empty[String,String]
      val occupied = companion.toList.flatMap(_.templ.stats).flatMap {
        case d: Defn.Type => List(d.name.value)
        case d: Defn.Object => List(d.name.value)
        case d: Defn.Class => List(d.name.value)
        case d: Defn.Trait => List(d.name.value)
        case d: Defn.Enum => List(d.name.value)
        case _ => Nil
      }.toSet
      ctor.paramClauses.filter(_.mod.isEmpty).flatMap(_.values).foreach { p =>
        val fieldKey = owner + "." + p.name.value
        val handled = scala.collection.mutable.Set.empty[Int]
        buffers.get(fieldKey).foreach { element =>
          require(Set("Byte","Int")(element),"Unsupported reviewed buffer element: " + fieldKey)
          val name = names.getOrElse(fieldKey,upper(p.name.value))
          p.decltpe.toList.flatMap(_.collect { case a: Type.Apply if Set("Array","scala.Array","_root_.scala.Array")(a.tpe.syntax) => a }).foreach { a =>
            require(a.argClause.values.size == 1 && !ancestry.exists(_.is[Defn.Def]),"Unsupported buffer owner or shape: " + fieldKey)
            val leaf = a.argClause.values.head
            val correction = base(leaf).isEmpty
            val baseType = "_root_.scala.Array[" + qualified(element) + "]"
            if(correction) {
              require(leaf.syntax == className.value + "." + name,"Unexpected prior buffer element: " + fieldKey)
              val obj = companion.toList.flatMap(_.templ.stats).collectFirst { case o: Defn.Object if o.name.value == name => o }.getOrElse(sys.error("Missing prior buffer type"))
              require(obj.templ.inits.size == 1 && obj.templ.stats.isEmpty)
              val parent = obj.templ.inits.head.tpe.asInstanceOf[Type.Apply]
              require(parent.tpe.syntax.endsWith("RefinedType") && parent.argClause.values.size == 2 &&
                parent.argClause.values.head.syntax == qualified(element) && parent.argClause.values(1).syntax.endsWith(".Pure"),"Prior buffer constraint/base differs: " + fieldKey)
              result += ((parent.argClause.values.head.pos.start,parent.argClause.values.head.pos.end,baseType))
            } else {
              require(base(leaf).contains(element) && !occupied(name),"Buffer base or companion collision: " + fieldKey)
              declarations(name) = baseType
            }
            handled += a.pos.start
            result += ((a.pos.start,a.pos.end,className.value + "." + name))
            evidence += List(path,(a.pos.startLine + 1).toString,(if(correction) "CORRECTION " else "") + owner + "#" + p.name.value + "/buffer " + a.structure,className.value + "." + name)
          }
        }
        p.decltpe.toList.flatMap(leaves(_)).filterNot { case(t,_,_) => parents(t).exists(x => handled(x.pos.start)) }.foreach { case(t,b,suffix) =>
          val key = owner + "." + p.name.value + (if(suffix.isEmpty) "" else "." + suffix)
          val defaultName = upper(p.name.value) + suffix
          val name = names.getOrElse(key,if(defaultName == className.value) "Value" else defaultName)
          val mappings = reuse.toList.collect { case(pattern,value) if
            key.matches(pattern.split("\\*",-1).map(java.util.regex.Pattern.quote).mkString(".*")) => value }.distinct
          require(mappings.size <= 1,"Conflicting concept mappings: " + key)
          val target = mappings.headOption.filterNot(t => t == owner + "." + name && !occupied(name))
          val shadowed = tree.collect { case n: Type.Name if n.value == b && n.pos.start != t.pos.start &&
            n.parent.exists {
              case d: Defn.Type => d.name eq n
              case d: Defn.Class => d.name eq n
              case _ => false
            } => n }.nonEmpty
          val reason = defer.get(owner + "." + p.name.value)
          val packed = parents(t).takeWhile(x => x.pos.start >= p.pos.start && x.pos.end <= p.pos.end).exists {
            case a: Type.Apply => Set("Array","scala.Array","_root_.scala.Array","js.Array")(a.tpe.syntax)
            case _ => false
          }
          val blocked = reason.nonEmpty || packed || enumCompanion.exists(_.templ.stats.isEmpty) || !name.matches("[A-Z][A-Za-z0-9_]*") || (ancestry.exists(_.is[Defn.Def]) && !localModels.contains(owner)) || shadowed ||
            (target.isEmpty && (occupied(name) || companion.exists(_.templ.stats.isEmpty) || declarations.get(name).exists(_ != qualified(b))))
          if(blocked) evidence += List(path,(t.pos.startLine + 1).toString,"UNRESOLVED " + key,t.structure + reason.map(" deferred: " + _).getOrElse(""))
          else {
            val replacement = target.getOrElse(referenceOwner + "." + name)
            if(target.isEmpty) declarations(name) = qualified(b)
            result += ((t.pos.start,t.pos.end,replacement))
            evidence += List(path,(t.pos.startLine + 1).toString,owner + "#" + p.name.value + "/" + suffix + " " + t.structure,replacement)
          }
        }
      }
      if(declarations.nonEmpty) {
        val pad = companion.map(o => indent(o.templ.stats.head)).getOrElse {
          if(enclosingEnum.nonEmpty) enumCompanion.map(o => indent(o.templ.stats.head) + "  ").getOrElse(indent(enclosingEnum.get) + "    ")
          else indent(c) + "  "
        }
        val definitions = declarations.toList.flatMap { case(n,b) => List(
          "type " + n + " = " + n + ".T",
          "object " + n + " extends _root_.io.github.iltotore.iron.RefinedType[" + b +
            ", _root_.io.github.iltotore.iron.constraint.any.Pure]") }.mkString("\n" + pad)
        companion match {
          case Some(o) => val start = o.templ.stats.head.pos.start; result += ((start,start,definitions + "\n\n" + pad))
          case None if enclosingEnum.nonEmpty =>
            enumCompanion match {
              case Some(o) =>
                val at = o.templ.stats.head.pos.start
                val outerPad = indent(o.templ.stats.head)
                result += ((at,at,"object " + className.value + ":\n" + pad + definitions + "\n\n" + outerPad))
              case None =>
                val e = enclosingEnum.get
                result += ((e.pos.end,e.pos.end,"\n\n" + indent(e) + "object " + e.name.value + ":\n" + indent(e) + "  object " + className.value + ":\n" + pad + definitions))
            }
          case None => result += ((c.pos.end,c.pos.end,"\n\n" + indent(c) + "object " + className.value + ":\n" + pad + definitions))
        }
      }
    }
    result.toList
  }
}
