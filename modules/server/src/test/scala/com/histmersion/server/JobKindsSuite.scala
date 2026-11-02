package com.histmersion.server

import com.histmersion.api.Wording

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Every kind of background job the server starts is a registered one (`com.histmersion.api.RegisteredJobKinds.all`),
  * so the web UI has a name for it ([[Wording.jobKind]]) and never shows `basemap-fetch` to a reader.
  */
class JobKindsSuite extends munit.FunSuite:

  test("the server's job kinds are the dictionary's"):
    val kinds = List(
      com.histmersion.util.basemap.BasemapService.JobKind,
      com.histmersion.api.catalogue.places.PlacesJobKinds.PlacesFetch.kind.value,
      com.histmersion.api.catalogue.places.PlacesJobKinds.PlacesHierarchy.kind.value,
      com.histmersion.domain.catalogue.events.EventImportsControllers.ImportJobKind,
      com.histmersion.domain.catalogue.evidence.EventLinksControllers.CandidatesJobKind,
      com.histmersion.domain.catalogue.places.SiteTypesControllers.SiteTypesJobKind,
      com.histmersion.domain.catalogue.events.EventImportsControllers.RederiveJobKind,
      com.histmersion.domain.media.imports.ImportsControllers.ImportJobKind,
      com.histmersion.domain.media.covers.CoversControllers.CoversJobKind,
      Bootstrap.JobKind
    )
    assertEquals(kinds.filterNot(k => com.histmersion.api.RegisteredJobKinds.all.parse(k).isDefined), Nil)
    kinds.foreach(k => assert(Wording.jobKindOf(k) != k, k))

  test("no job kind is spelled as a literal outside the dictionary (a new one starts as a registered JobKind)"):
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
    val dirs = List("modules/server/src/main/scala", "modules/api/src/main/scala")
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
