package com.histmersion.domain.personal

import com.histmersion.api.personal.NoteText
import com.histmersion.api.ItemKind as MarkKind
import com.histmersion.domain.ItemKey
import com.histmersion.domain.personal.notes.NoteEditRefused

/** A person's notes, in memory and in the database. */
class NoteServiceContractSuite extends PersonalServiceContract("note"):

  both("notes: left on an item and on a point, found by the item's new key, edited with the time last seen, removed") {
    w =>
      w.addSource("zz_pers_n", "Zz noted source")
      val n1 = w.notes.add(me, item(MarkKind.Source, "zz_pers_n"), NoteText("Compare with **Langgut**."), None, at(0))
      val n2 = w.notes.add(me, point, NoteText("Bridge here?"), None, at(1))
      assertEquals(n1.title.value: String, "Zz noted source")
      assertEquals(n2.title.value: String, "Place on the map")
      assertEquals(n2.target, point.withoutZoom, "a note keeps no zoom")
      val everything = SearchWords.Everything
      assertEquals(w.notes.list(me, None, None, everything, all).map(_.id), List(n2.id, n1.id))
      w.rename("zz_pers_n", "zz_pers_n2")
      assertEquals(
        w.notes.list(me, Some(MarkKind.Source), Some(ItemKey("zz_pers_n2")), everything, all).map(_.id),
        List(n1.id)
      )
      assertEquals(w.notes.list(me, None, Some(ItemKey("zz_pers_n")), everything, all).map(_.id), List(n1.id))
      assertEquals(w.notes.list(me, None, None, SearchWords.of(Some("langgut")), all).map(_.id), List(n1.id))
      assertEquals(n1.updatedAt.iso, "2026-10-30T12:00:00.123456Z")
      val edited = w.notes.edit(me, n1.id, NoteText("Changed."), Some(n1.updatedAt), at(5))
      assertEquals(edited.map(_.text.value.toString), Right("Changed."))
      assertEquals(
        w.notes.edit(me, n1.id, NoteText("Stale."), Some(n1.updatedAt), at(6)),
        Left(NoteEditRefused.Changed(edited.toOption.get.updatedAt))
      )
      assertEquals(w.notes.list(me, None, None, everything, all).map(_.id), List(n1.id, n2.id))
      assertEquals(w.notes.remove(me, n1.id, at(7)).map(_.text.value.toString), Some("Changed."))
      assertEquals(w.notes.get(me, n1.id), None)
      assertEquals(w.notes.edit(me, n1.id, NoteText("x"), None, at(8)), Left(NoteEditRefused.Missing))
      assertEquals(w.notes.get(other, n2.id), None)
  }
