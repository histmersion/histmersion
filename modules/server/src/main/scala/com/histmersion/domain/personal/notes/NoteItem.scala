package com.histmersion.domain.personal.notes

import com.histmersion.domain.{ItemKey, ItemUid}
import com.histmersion.domain.personal.PersonalState.StoredNote

/** The item a list of notes is narrowed to: the key it was asked by, and the uids of the records that key names now —
  * so a note left on an item before its rename is found by the new key.
  */
final case class NoteItem(key: ItemKey, uids: Set[ItemUid]):
  def holds(note: StoredNote): Boolean = note.target.itemRef.exists(r => r.key == key || r.uid.exists(uids))
