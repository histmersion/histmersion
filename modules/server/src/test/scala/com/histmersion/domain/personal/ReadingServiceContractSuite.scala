package com.histmersion.domain.personal

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.ItemKey
import com.histmersion.domain.personal.reading.ReadingShow
import com.histmersion.{Offset, PageSize, UserId}

/** A person's read marks and what is not read yet, in memory and in the database. */
class ReadingServiceContractSuite extends PersonalServiceContract("reading"):

  both("read marks: only marked read is read; opening is kept apart; unread lists the rest by title") { w =>
    w.addSource("zz_r_b", "Zz Beta")
    w.addSource("zz_r_a", "Zz Alpha")
    w.addSource("zz_r_c", "Zz Gamma")
    val b = ItemKey("zz_r_b")
    val o = w.reading.opened(me, source, b, None, at(0))
    assertEquals((o.readAt, o.title.value: String), (None, "Zz Beta"))
    val r = w.reading.setRead(me, source, b, read = com.histmersion.domain.personal.reading.ReadingService.Read(true), None, at(1))
    assert(r.readAt.isDefined && r.openedAt.isDefined)
    w.reading.setRead(me, source, b, read = com.histmersion.domain.personal.reading.ReadingService.Read(true), None, at(2))
    assertEquals(
      w.reading.list(me, None, ReadingShow.Read, all).flatMap(_.readAt.map(_.iso)),
      List("2026-10-30T12:01:00.123456Z")
    )
    def unread(user: UserId, size: Int = 10, from: Int = 0) =
      w.reading
        .unread(user, source, PageSize.applyUnsafe(size), Offset.applyUnsafe(from))
        .items
        .map(_.key.value: String)
    assertEquals(unread(me), List("zz_r_a", "zz_r_c"))
    assertEquals(unread(other), List("zz_r_a", "zz_r_b", "zz_r_c"))
    w.rename("zz_r_b", "zz_r_b2")
    assertEquals(unread(me), List("zz_r_a", "zz_r_c"), "a read source stays read under its new key")
    assertEquals(w.reading.list(me, None, ReadingShow.Read, all).map(_.item.ref.key.value: String), List("zz_r_b2"))
    w.reading.setRead(me, source, b, read = com.histmersion.domain.personal.reading.ReadingService.Read(false), None, at(3))
    assertEquals(w.reading.list(me, None, ReadingShow.Read, all), Nil)
    assertEquals(w.reading.list(me, None, ReadingShow.Opened, all).map(_.title.value: String), List("Zz Beta"))
    val article = MarkTarget.itemKind(MarkKind.Article)
    w.reading.opened(me, article, ItemKey("zz_art_x"), TargetLabel.tidy("Zz article X"), at(4))
    assertEquals(
      w.reading.list(me, None, ReadingShow.Opened, all).map(_.title.value: String),
      List("Zz article X", "Zz Beta")
    )
    assertEquals(
      w.reading.list(me, Some(source), ReadingShow.All, all).map(_.item.ref.key.value: String),
      List("zz_r_b2")
    )
    assertEquals(unread(me, 1, 1), List("zz_r_b2"))
  }
