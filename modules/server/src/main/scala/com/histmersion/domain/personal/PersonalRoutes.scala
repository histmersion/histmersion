package com.histmersion
package domain.personal

import com.histmersion.api.Paging
import com.histmersion.api.personal.PersonalApi
import com.histmersion.domain.Caller
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** The person's endpoints bound to their controllers, in the order route matching needs: `reading/unread` and
  * `reading/opened` before `reading`. Every request is the local person's until there are accounts.
  */
object PersonalRoutes:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  def apply(personal: PersonalControllers): List[ServerEndpoint[Any, Identity]] =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
    val me = Caller.Local
    List(
      PersonalApi.bookmarks.serverLogic[Identity]((kind, q, limit) =>
        personal.listBookmarks(me, kind, q, limit.map(Paging.Limit(_)))
      ),
      PersonalApi.addBookmark.serverLogic[Identity](in => personal.addBookmark(me, in)),
      PersonalApi.editBookmark.serverLogic[Identity]((id, in) => personal.editBookmark(me, id, in)),
      PersonalApi.removeBookmark.serverLogic[Identity](id => personal.removeBookmark(me, id)),
      PersonalApi.notes.serverLogic[Identity]((kind, id, q, limit) =>
        personal.listNotes(me, kind, id, q, limit.map(Paging.Limit(_)))
      ),
      PersonalApi.note.serverLogic[Identity](id => personal.note(me, id)),
      PersonalApi.addNote.serverLogic[Identity](in => personal.addNote(me, in)),
      PersonalApi.editNote.serverLogic[Identity]((id, in) => personal.editNote(me, id, in)),
      PersonalApi.removeNote.serverLogic[Identity](id => personal.removeNote(me, id)),
      PersonalApi.unread.serverLogic[Identity]((kind, limit, offset) =>
        personal.unread(me, kind, limit.map(Paging.Limit(_)), offset)
      ),
      PersonalApi.opened.serverLogic[Identity](in => personal.opened(me, in)),
      PersonalApi.reading.serverLogic[Identity]((kind, show, limit) =>
        personal.readMarks(me, kind, show, limit.map(Paging.Limit(_)))
      ),
      PersonalApi.setRead.serverLogic[Identity](in => personal.setRead(me, in))
    )
