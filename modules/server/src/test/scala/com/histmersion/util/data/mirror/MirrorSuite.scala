package com.histmersion.util.data.mirror

import com.histmersion.api.ApiError
import com.histmersion.domain.research.ResearchData
import com.histmersion.server.Deps
import com.histmersion.domain.library.LibraryData
import com.histmersion.util.data.DataOwner

/** The mirror over the registered notes: a kind's note from its owner's renderer, an unregistered kind refused; today's
  * registration — the library's source notes in `sources/`, research's article notes by kind.
  */
class MirrorSuite extends munit.FunSuite:

  private val sources = new MirrorNote:
    val records: MirrorRecords = LibraryData.SourceNotes
    def note(citekey: String): Either[ApiError, String] = Right(s"source $citekey")

  private val articles = new MirrorNote:
    val records: MirrorRecords = ResearchData.ArticleNotes
    def note(slug: String): Either[ApiError, String] =
      if slug == "gone" then Left(ApiError("no such article")) else Right(s"article $slug")

  private val mirror = Mirror(Deps.data.mirror, List(sources, articles))

  test("today's registration"):
    assertEquals(
      Deps.data.mirror.map(e => (e.owner, e.records, e.placement)),
      List(
        (DataOwner("library"), LibraryData.SourceNotes, MirrorPlacement.SourceFiles),
        (DataOwner("research"), ResearchData.ArticleNotes, MirrorPlacement.ArticleFiles)
      )
    )

  test("a note from the registered member of its kind"):
    assertEquals(mirror.note(LibraryData.SourceNotes, "anthony2007_horse"), Right("source anthony2007_horse"))
    assertEquals(mirror.note(ResearchData.ArticleNotes, "zz"), Right("article zz"))
    assertEquals(mirror.note(ResearchData.ArticleNotes, "gone").left.map(_.message), Left("no such article"))

  test("a kind nobody registered is refused"):
    assert(mirror.note(MirrorRecords("events"), "x").isLeft)
    assertEquals(mirror.kinds, List(LibraryData.SourceNotes, ResearchData.ArticleNotes))
