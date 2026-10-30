package com.histmersion.server

import com.histmersion.api.{JobKind, Wording}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Every kind of background job the server starts is one of `api.JobKind`, so the web UI has a name for it
  * ([[Wording.jobKind]]) and never shows `basemap-fetch` to a reader.
  */
class JobKindsSuite extends munit.FunSuite:

  test("the server's job kinds are the dictionary's"):
    val kinds = List(
      basemap.BasemapService.JobKind,
      Handlers.PlacesFetchJobKind,
      Handlers.PlacesHierarchyJobKind,
      events.EventCatalogHandlers.ImportJobKind,
      events.EventCatalogHandlers.CandidatesJobKind,
      events.EventCatalogHandlers.SiteTypesJobKind,
      events.EventCatalogHandlers.RederiveJobKind,
      media.MediaHandlers.ImportJobKind,
      media.MediaHandlers.CoversJobKind,
      Bootstrap.JobKind
    )
    assertEquals(kinds.filterNot(k => JobKind.parse(k).isDefined), Nil)
    kinds.foreach(k => assert(Wording.jobKindOf(k) != k, k))

  test("no job kind is spelled as a literal outside the dictionary (a new one starts as a JobKind)"):
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
    val dirs = List("modules/server/src/main/scala", "modules/api/shared/src/main/scala")
      .map(root.resolve)
      .filter(Files.isDirectory(_))
    assert(dirs.nonEmpty, s"no sources under $root")
    val literal = """\bval \w*(JobKind|KindIngest|KindReocr)\w*\s*=\s*"""".r
    val hits = dirs
      .flatMap(d => Files.walk(d).iterator.asScala.filter(_.toString.endsWith(".scala")).toList)
      .flatMap(p =>
        Files.readAllLines(p).asScala.zipWithIndex.collect {
          case (line, i) if literal.findFirstIn(line).isDefined => s"${p.getFileName}:${i + 1} ${line.trim}"
        }
      )
    assertEquals(hits, Nil)
