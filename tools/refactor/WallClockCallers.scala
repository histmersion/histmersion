import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class WallClockCallers extends SyntacticRule("WallClockCallers") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val frozen = Files.readAllLines(Paths.get("refactor-audit/wall-clock/build-1/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val out = Paths.get("refactor-audit/wall-clock/callers-1")
    require(!Files.exists(out))
    val evidence = scala.collection.mutable.ListBuffer.empty[String]
    val outputs = frozen.keys.toList.sorted.filter(_.startsWith("modules/server/src/")).flatMap { path =>
      val source = Files.readString(Paths.get(path)); require(hash(source) == frozen(path))
      val tree = source.parse[Source].get
      val file = Paths.get(path).getFileName.toString
      val isTest = path.contains("/src/test/")
      val edits = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
      def edit(t: Tree, value: String): Unit = { edits += ((t.pos.start,t.pos.end,value)); evidence += path + "\t" + (t.pos.startLine+1) + "\t" + t.structure.replace("\n","\\n") + "\t" + value.replace("\n","\\n") }
      def insert(at: Int, value: String): Unit = edits += ((at,at,value))
      def one[A](xs: List[A]): A = { require(xs.size == 1, path + ": expected one, got " + xs.size); xs.head }
      def required(name: String): Unit = tree.collect { case p: Term.Param if p.name.value == name && p.default.nonEmpty => p }.foreach(p => edit(p,p.copy(default=None).syntax))
      def addArg(a: Term.Apply, value: String): Unit = {
        val close = a.argClause.tokens.reverseIterator.find(_.is[Token.RightParen]).get
        insert(close.pos.start, (if(a.argClause.values.isEmpty) "" else ", ") + value)
      }
      def using(a: Term.Apply, value: String): Unit = insert(a.pos.end,"(using " + value + ")")
      if(!isTest) file match {
        case "AuthHandler.scala" => required("tokens")
        case "ServerPlugins.scala" | "ServerGraph.scala" | "Indexes.scala" | "OaFetch.scala" => required("core")
        case "AcquisitionControllers.scala" =>
          val c = one(tree.collect { case c: Defn.Class if c.name.value == "AcquisitionControllers" => c })
          insert(c.ctor.pos.end,"(using clock: java.time.Clock)")
        case "Composition.scala" =>
          tree.collect { case p: Term.Param if p.name.value == "core" && p.default.nonEmpty => p }.foreach(p => edit(p,"core: Option[CoreBudget] = None"))
          val build = one(tree.collect { case d: Defn.Def if d.name.value == "build" => d })
          val first = build.body.asInstanceOf[Term.Block].stats.head
          insert(first.pos.start,"val selectedCore = core.getOrElse(CoreBudget(() => system.clock.instant()))\n    ")
          build.body.collect { case n: Term.Name if n.value == "core" => n }.foreach(n => edit(n,"selectedCore"))
          val acquire = one(tree.collect { case d: Defn.Def if d.name.value == "acquireClients" => d })
          insert(acquire.paramClauseGroups.last.paramClauses.last.pos.end,"(using clock: java.time.Clock)")
        case "Main.scala" =>
          val core = one(tree.collect { case v: Defn.Val if v.pats.exists(_.syntax == "core") => v })
          insert(core.pos.start,"val startupClock = java.time.Clock.systemUTC()\n  ")
          val origin = one(tree.collect { case a: Term.Apply if a.fun.syntax == "java.time.Clock.systemUTC" => a })
          edit(origin,"startupClock")
        case _ => ()
      }
      tree.collect { case a: Term.Apply => a }.foreach { a =>
        val fun = a.fun.syntax
        val args = a.argClause.values
        def named(n: String) = args.exists { case Term.Assign(Term.Name(k),_) => k == n; case _ => false }
        def has(n: String, position: Int) = named(n) || args.takeWhile(!_.isInstanceOf[Term.Assign]).size > position
        if(file == "AgentsControllers.scala" && fun == "ModelPicker") using(a,"clock")
        if(file == "AcquisitionControllers.scala" && fun == "CandidateSearch") addArg(a,"today = () => java.time.LocalDate.now(clock.withZone(java.time.ZoneOffset.UTC))")
        if(Set("AcquisitionWiring.scala","AcquisitionTesting.scala")(file) && fun == "AcquisitionControllers") using(a,"deps.clock")
        if(file == "Main.scala" && fun == "CoreBudget" && args.isEmpty) addArg(a,"() => startupClock.instant()")
        if(file == "Composition.scala" && fun == "acquireClients") using(a,"deps.clock")
        if(file == "Composition.scala" && fun == "SttpHttp") addArg(a,"clock = () => clock.instant()")
        if(Set("Composition.build","Composition.server")(fun)) args.collect {
          case Term.Assign(Term.Name("core"), value) => edit(value,"Some(" + value.syntax + ")")
        }
        if(isTest) {
          val real = "() => java.time.Instant.now(java.time.Clock.systemUTC())"
          if((fun == "CoreBudget" || fun.endsWith(".acquisition.CoreBudget")) && args.isEmpty) addArg(a,real)
          if((fun == "SttpHttp" || fun.endsWith(".acquisition.SttpHttp")) && !has("clock",4)) addArg(a,"clock = " + real)
          val corePosition = fun match {
            case "ServerPlugins.load" | "com.histmersion.server.ServerPlugins.load" => Some(3)
            case "ServerPlugins.fetchers" | "OaFetch" => Some(2)
            case "Indexes.of" => Some(3)
            case _ => None
          }
          corePosition.filterNot(p => has("core",p)).foreach(_ => addArg(a,"core = com.histmersion.domain.library.acquisition.CoreBudget(" + real + ")"))
        }
      }
      if(edits.isEmpty) None else {
        val sorted = edits.toList.sortBy(_._1)
        require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true },"Overlapping edits " + path)
        val next = sorted.reverse.foldLeft(source) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
        next.parse[Source].get
        Some((path,source,next))
      }
    }
    require(outputs.nonEmpty)
    Files.createDirectories(out)
    outputs.foreach { case(p,b,n) => List("before" -> b,"sources" -> n).foreach { case(dir,s) => val dest = out.resolve(dir).resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s) } }
    Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: outputs.map { case(p,b,n) => s"$p\t${hash(b)}\t${hash(n)}" }).asJava)
    Files.write(out.resolve("shapes.tsv"),("path\tline\tast\treplacement" :: evidence.toList).asJava)
    println(s"Generated ${outputs.size} caller files; explicit test callbacks preserve preceding real-time behavior")
  }
}
