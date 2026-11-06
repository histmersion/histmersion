package com.histmersion.util.data.mirror

import com.histmersion.testsupport.ComparedByValue.given

/** The mirror's layout: `sources/<citekey>.md`, `<kind>/<slug>.md`, `meta/<FILE>`. */
class MirrorPlacementSuite extends munit.FunSuite:

  test("each placement's directory under the export's"):
    assertEquals(MirrorPlacement.SourceFiles.directory, Some("sources"))
    assertEquals(MirrorPlacement.ArticleFiles.directory, None)
    assertEquals(MirrorPlacement.MetaFiles.directory, Some("meta"))
    assertEquals(MirrorPlacement.SourceFiles, MirrorPlacement.InDirectory(MirrorPlacement.SourcesDirectory))
    assertEquals(MirrorPlacement.MetaFiles, MirrorPlacement.Named(MirrorPlacement.MetaDirectory))

  test("a record's note file"):
    assertEquals(MirrorPlacement.noteFile(com.histmersion.util.data.mirror.MirrorPlacement.RecordId("anthony2007_horse")), "anthony2007_horse.md")
