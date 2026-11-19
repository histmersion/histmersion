package com.histmersion
package domain.personal

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.ItemKind
import com.histmersion.api.personal.{MapZoom, Target}
import com.histmersion.domain.personal.bookmarks.Bookmark
import com.histmersion.domain.personal.notes.Note

/** What the server checks of a personal mark a client sent, once, where the api value becomes the domain's. */
class PersonalChecksSuite extends munit.FunSuite:

  test("a bookmark's or a note's id is a lower-case UUID; any case is read, anything else refused"):
    assertEquals(
      Bookmark
        .idOf(api.personal.Bookmark.Id("0190F3C2-1111-7000-8000-000000000001"))
        .map(_.value: String),
      Right("0190f3c2-1111-7000-8000-000000000001")
    )
    assert(Note.idOf(api.personal.Note.Id("not")).isLeft)
    assert(Note.idOf(api.personal.Note.Id("")).isLeft)

  test("a target's zoom is one the map has"):
    val view = Target(ItemKind.View)
    assertEquals(MarkTarget.zoomChecked(view), Right(view))
    assert(MarkTarget.zoomChecked(view.modify(_.zoom).setTo(Some(MapZoom(24.0)))).isRight)
    assert(MarkTarget.zoomChecked(view.modify(_.zoom).setTo(Some(MapZoom(24.5)))).isLeft)
    assert(MarkTarget.zoomChecked(view.modify(_.zoom).setTo(Some(MapZoom(-1.0)))).isLeft)
