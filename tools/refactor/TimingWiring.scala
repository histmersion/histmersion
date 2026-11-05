import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TimingWiring extends SyntacticRule("TimingWiring") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val timer = "com.histmersion.util.time.Timer"
    val pause = "com.histmersion.util.time.Pause"
    val realTimer = "new com.histmersion.util.time.SystemTimer"
    val realPause = "new com.histmersion.util.time.ThreadPause"
    val zero = s"new $timer { def start(): $timer.Measure = new $timer.Measure { def elapsed: scala.concurrent.duration.FiniteDuration = scala.concurrent.duration.Duration.Zero } }"
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val frozen = Files.readAllLines(Paths.get("refactor-audit/monotonic/capabilities-build/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val first = Paths.get("refactor-audit/monotonic/preview-1/sources")
    val out = Paths.get("refactor-audit/monotonic/wiring-1"); require(!Files.exists(out))
    val outputs = frozen.keys.toList.sorted.flatMap { path =>
      val before = Files.readString(Paths.get(path)); require(hash(before) == frozen(path))
      val source = if(Files.exists(first.resolve(path))) Files.readString(first.resolve(path)) else before
      val tree = source.parse[Source].get
      val file = Paths.get(path).getFileName.toString
      val test = path.contains("/src/test/")
      val edits = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
      def insert(at: Int, value: String): Unit = edits += ((at,at,value))
      def edit(t: Tree,value: String): Unit = edits += ((t.pos.start,t.pos.end,value))
      def addParams(c: Term.ParamClause,value: String): Unit = insert(c.values.last.pos.end,", " + value)
      def cls(name: String): Defn.Class = { val cs = tree.collect { case c: Defn.Class if c.name.value == name => c }; require(cs.size == 1,path); cs.head }
      def contextual(name: String): Unit = insert(cls(name).ctor.pos.end,s"(using timer: $timer)")
      def using(a: Term.Apply, value: String): Unit = insert(a.pos.end,"(using " + value + ")")
      def addArg(a: Term.Apply,value: String): Unit = insert(a.argClause.tokens.reverseIterator.find(_.is[Token.RightParen]).get.pos.start,", " + value)
      if(!test) file match {
        case "SystemContext.scala" => addParams(cls("SystemContext").ctor.paramClauses.last,s"timer: $timer = $realTimer, pause: $pause = $realPause")
        case "Deps.scala" =>
          addParams(cls("Deps").ctor.paramClauses.last,s"val timer: $timer = $realTimer, val pause: $pause = $realPause")
          tree.collect { case d: Defn.Def if d.name.value == "apply" && d.paramClauseGroups.exists(_.paramClauses.exists(_.values.exists(_.name.value == "clock"))) => d }.foreach(d => addParams(d.paramClauseGroups.head.paramClauses.last,s"timer: $timer = $realTimer, pause: $pause = $realPause"))
          tree.collect { case n: Term.New if n.init.tpe.syntax == "Deps" => n }.foreach(n => insert(n.init.argClauses.last.values.last.pos.end,", timer = timer, pause = pause"))
        case "ServerGraph.scala" => tree.collect { case n: Term.New if n.init.tpe.syntax == "Deps" => n }.foreach(n => insert(n.init.argClauses.last.values.last.pos.end,", timer = system.timer, pause = system.pause"))
        case "EngineSite.scala" =>
          val clock = tree.collect { case d: Decl.Def if d.name.value == "clock" => d }; require(clock.size == 1)
          insert(clock.head.pos.end,s"\n  def timer: $timer\n  def pause: $pause")
        case "ServerEngineSite.scala" =>
          val clock = tree.collect { case d: Defn.Def if d.name.value == "clock" => d }; require(clock.size == 1)
          insert(clock.head.pos.end,s"\n  def timer: $timer = deps.timer\n  def pause: $pause = deps.pause")
        case "SourcePassages.scala" => contextual("SourcePassages"); contextual("Text")
        case "SourcesControllers.scala" => contextual("SourcesControllers")
        case "CheckContext.scala" => addParams(cls("CheckContext").ctor.paramClauses.last,s"timer: $timer")
        case "CheckControllers.scala" => contextual("CheckControllers")
        case _ => ()
      }
      if(test && file == "PassageFinderSuite.scala") {
        val c = cls("PassageFinderSuite")
        insert(c.templ.body.stats.head.pos.start,s"private given $timer = $zero\n\n  ")
      }
      if(test && file == "SearchFixtures.scala") {
        val obj = tree.collect { case o: Defn.Object if o.name.value == "SearchFixtures" => o }.head
        insert(obj.templ.body.stats.head.pos.start,s"private given $timer = $zero\n\n  ")
      }
      tree.collect { case a: Term.Apply => a }.foreach { a =>
        val fun = a.fun.syntax
        if(!test) {
          if(file == "RunCtx.scala" && fun == "LatencyInterceptor") using(a,"engine.agents.timer")
          if(file == "Deps.scala" && fun.endsWith("SourcePassages")) using(a,"timer")
          if(file == "LibraryWiring.scala" && Set("SourcesControllers","QueryService")(fun)) using(a,"deps.timer")
          if(file == "CatalogueWiring.scala" && fun == "PlaceGeometry") using(a,"deps.timer")
          if(file == "PlacesData.scala" && fun == "PlaceGeometry") using(a,"c.timer")
          if(file == "CheckControllers.scala" && fun == "CheckContext") addArg(a,"timer")
          if(file == "ServerWiring.scala" && fun == "CheckControllers") using(a,"deps.timer")
        } else {
          if(Set("SourcesControllers","PlaceGeometry")(fun)) using(a,realTimer)
          if(fun == "CheckContext") addArg(a,realTimer)
          if(file == "PassageFinderSuite.scala" && fun == "PassageFinder.find" && a.argClause.values.last.syntax == "50")
            using(a,s"new $timer { def start(): $timer.Measure = new $timer.Measure { def elapsed: scala.concurrent.duration.FiniteDuration = scala.concurrent.duration.FiniteDuration(51, scala.concurrent.duration.MILLISECONDS) } }")
        }
      }
      val sorted = edits.toList.sortBy(_._1)
      require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true },"Overlapping edits " + path)
      val next = sorted.reverse.foldLeft(source) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
      if(next == before) None else { next.parse[Source].get; Some((path,before,next)) }
    }
    Files.createDirectories(out)
    outputs.foreach { case(p,b,n) => List("before" -> b,"sources" -> n).foreach { case(dir,s) => val dest = out.resolve(dir).resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s) } }
    Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: outputs.map { case(p,b,n) => s"$p\t${hash(b)}\t${hash(n)}" }).asJava)
    println(s"Generated ${outputs.size} files combining timing consumers and caller propagation")
  }
}
