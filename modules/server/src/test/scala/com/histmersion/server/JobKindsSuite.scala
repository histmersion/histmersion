package com.histmersion
package server

import com.histmersion.api.Wording
import com.histmersion.api.jobs.JobKinds

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.util.basemap.BasemapService
import com.histmersion.api.catalogue.PlacesJobKinds
import com.histmersion.domain.catalogue.events.EventImportsControllers
import com.histmersion.domain.catalogue.evidence.EventLinksControllers
import com.histmersion.domain.catalogue.places.SiteTypesControllers
import com.histmersion.domain.media.imports.ImportsControllers
import com.histmersion.domain.media.covers.CoversControllers
import com.histmersion.api.RegisteredJobKinds
import com.histmersion.lint.{tolerated, LintRule}

/** Every kind of background job the server starts is a registered one (`com.histmersion.api.RegisteredJobKinds.all`),
  * so the web UI has a name for it ([[Wording.jobKind]]) and never shows `basemap-fetch` to a reader.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class JobKindsSuite extends munit.FunSuite:

  test("the server's job kinds are the dictionary's"):
    val kinds = List(
      JobKinds.Wire(BasemapService.JobKind.value),
      PlacesJobKinds.PlacesFetch.kind,
      PlacesJobKinds.PlacesHierarchy.kind,
      JobKinds.Wire(EventImportsControllers.ImportJobKind.value),
      JobKinds.Wire(EventLinksControllers.CandidatesJobKind.value),
      JobKinds.Wire(SiteTypesControllers.SiteTypesJobKind.value),
      JobKinds.Wire(EventImportsControllers.RederiveJobKind.value),
      JobKinds.Wire(ImportsControllers.ImportJobKind.value),
      JobKinds.Wire(CoversControllers.CoversJobKind.value),
      JobKinds.Wire(Bootstrap.JobKind.value)
    )
    assertEquals(kinds.filterNot(k => RegisteredJobKinds.all.parse(k).isDefined), Nil)
    kinds.foreach(k => assert(Wording.jobKindOf(k) != k.value, k))

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
