package com.histmersion
package domain.personal

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.personal.{MapZoom, ViewState}
import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.{Latitude, Longitude, PageSize}
import com.histmersion.api.personal.Bookmark
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** A person's bookmarks, in memory and in the database. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class BookmarkServiceContractSuite extends PersonalServiceContract("bookmark"):

  both("an item is bookmarked once; a retitled source keeps its bookmark and shows its new title") { w =>
    w.addSource("zz_pers_a", "Zz the first title")
    val b1 = w.bookmarks.add(
      me,
      item(MarkKind.Source, "zz_pers_a"),
      None,
      TargetLabel.tidy(TargetLabel.Label("as I saw it")),
      at(0)
    )
    assertEquals(b1.title.value: String, "Zz the first title")
    val b2 = w.bookmarks.add(me, item(MarkKind.Source, "zz_pers_a"), Some(Bookmark.Label("mine")), None, at(1))
    assertEquals(b2.id, b1.id)
    assertEquals(b2.label.map(_.value.toString), Some("mine"))
    w.retitle("zz_pers_a", "Zz the second title")
    assertEquals(
      w.bookmarks.list(me, None, SearchWords.Everything, all).map(_.title.value: String),
      List("Zz the second title")
    )
    assertEquals(w.bookmarks.list(other, None, SearchWords.Everything, all), Nil)
  }

  both("a mark follows its source through a rename, a merge and a deletion when it is read") { w =>
    List("zz_m_a", "zz_m_b", "zz_m_c", "zz_m_d").foreach(ck => w.addSource(ck, s"Zz $ck"))
    List("zz_m_a", "zz_m_b", "zz_m_d").zipWithIndex.foreach((ck, i) =>
      w.bookmarks.add(me, item(MarkKind.Source, ck), None, TargetLabel.tidy(TargetLabel.Label(s"stored $ck")), at(i))
    )
    w.rename("zz_m_a", "zz_m_a2")
    w.merge("zz_m_b", "zz_m_c")
    w.delete("zz_m_d")
    val shown =
      w.bookmarks.list(me, None, SearchWords.Everything, all).map(b => (keyOf(b.target), b.title.value: String))
    assertEquals(shown, List(("zz_m_d", "stored zz_m_d"), ("zz_m_c", "Zz zz_m_c"), ("zz_m_a2", "Zz zz_m_a")))
    assertEquals(
      w.bookmarks.list(me, None, SearchWords.of(Some(SearchWords.Query("A2"))), all).map(b => keyOf(b.target)),
      List("zz_m_a2"),
      "the search matches the key the source has now"
    )
  }

  both(
    "an item the collection does not hold keeps the name it was bookmarked with; views and points are each their own"
  ) { w =>
    w.bookmarks.add(
      me,
      item(MarkKind.Place, "zz_place_1"),
      None,
      TargetLabel.tidy(TargetLabel.Label("Zz Tiryns")),
      at(0)
    )
    val v = MarkTarget.View(
      Some(ViewState("from=-1300&to=-1100&tag=zz")),
      Some(Latitude(37.6)),
      Some(Longitude(22.8)),
      Some(MapZoom(7.5)),
      None,
      None
    )
    w.bookmarks.add(me, v, Some(Bookmark.Label("Argolid")), None, at(1))
    w.bookmarks.add(me, v, Some(Bookmark.Label("Argolid again")), None, at(2))
    w.bookmarks.add(me, point, None, None, at(3))
    val listed = w.bookmarks.list(me, None, SearchWords.Everything, all)
    assertEquals(listed.map(_.wire.name), List("Place on the map", "Argolid again", "Argolid", "Zz Tiryns"))
    assertEquals(listed(1).target, v)
    assertEquals(w.bookmarks.list(me, Some(MarkKind.View), SearchWords.Everything, all).size, 2)
    assertEquals(
      w.bookmarks.list(me, None, SearchWords.of(Some(SearchWords.Query("tiryns"))), all).map(_.wire.name),
      List("Zz Tiryns")
    )
    assertEquals(w.bookmarks.list(me, None, SearchWords.Everything, PageSize(2)).size, 2)
  }

  both("a removed bookmark leaves the list; bookmarking the item again makes a new one") { w =>
    val b = w.bookmarks.add(
      me,
      item(MarkKind.Article, "zz_art"),
      None,
      TargetLabel.tidy(TargetLabel.Label("Zz article")),
      at(0)
    )
    assertEquals(w.bookmarks.remove(other, b.id, at(1)), None)
    assertEquals(w.bookmarks.remove(me, b.id, at(1)).map(_.id), Some(b.id))
    assertEquals(w.bookmarks.remove(me, b.id, at(2)), None)
    assertEquals(w.bookmarks.list(me, None, SearchWords.Everything, all), Nil)
    assertEquals(w.bookmarks.rename(me, b.id, None, at(2)), None)
    val again = w.bookmarks.add(me, item(MarkKind.Article, "zz_art"), None, None, at(3))
    assertNotEquals(again.id, b.id)
  }
