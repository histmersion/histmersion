package com.histmersion.util.data.mirror

import com.histmersion.api.ApiError
import com.histmersion.server.Deps
import com.histmersion.util.data.TablesAdapter

/** The mirror over the registered notes: a kind's note from its registered member, an unregistered kind refused;
  * today's registration — the library's source notes in `sources/`, research's article notes by kind.
  */
class MirrorSuite extends munit.FunSuite:

  private given TransitionNotes:
    def sourceNote(citekey: String): Either[ApiError, String] = Right(s"source $citekey")
    def articleNote(slug: String): Either[ApiError, String] =
      if slug == "gone" then Left(ApiError("no such article")) else Right(s"article $slug")

  private val mirror = Mirror(Deps.data.mirror)

  test("today's registration"):
    assertEquals(
      Deps.data.mirror.map(e => (e.owner, e.records, e.placement)),
      List(
        (TablesAdapter.LibraryOwner, MirrorAdapter.SourceNotes, MirrorPlacement.SourceFiles),
        (TablesAdapter.ResearchOwner, MirrorAdapter.ArticleNotes, MirrorPlacement.ArticleFiles)
      )
    )

  test("a note from the registered member of its kind"):
    assertEquals(mirror.note(MirrorAdapter.SourceNotes, "anthony2007_horse"), Right("source anthony2007_horse"))
    assertEquals(mirror.note(MirrorAdapter.ArticleNotes, "zz"), Right("article zz"))
    assertEquals(mirror.note(MirrorAdapter.ArticleNotes, "gone").left.map(_.message), Left("no such article"))

  test("a kind nobody registered is refused"):
    assert(mirror.note(MirrorRecords("events"), "x").isLeft)
    assertEquals(mirror.kinds, List(MirrorAdapter.SourceNotes, MirrorAdapter.ArticleNotes))
