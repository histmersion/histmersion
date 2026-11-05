package com.histmersion.domain.personal

import chimp.server.*
import com.histmersion.api.personal.PersonalMarkdown
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The person's bookmarks and notes for agents to READ: there is no tool that makes, changes or removes one — those are
  * the person's own, written in the web UI and with `histmersion bookmarks|notes`. An agent reads them for the local
  * person, whom it works for.
  */
object PersonalTools:
  import com.histmersion.util.mcp.ToolInputs.given

  final case class BookmarksInput(kind: Option[BookmarksInput.Kind] = None, query: Option[BookmarksInput.Query] = None, limit: Option[BookmarksInput.Limit] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object BookmarksInput:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class NotesInput(
      id: Option[NotesInput.Id] = None,
      kind: Option[NotesInput.Kind] = None,
      item: Option[NotesInput.Item] = None,
      query: Option[NotesInput.Query] = None,
      limit: Option[NotesInput.Limit] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object NotesInput:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def apply(personal: PersonalControllers) =
    val bookmarks =
      tool("bookmarks")
        .description(
          "READ ONLY: the person's own bookmarks, newest first — the sources, articles, events, places, pictures and " +
            "goals they marked, places on the map, saved views of Explore (filters, map position, span) and periods. " +
            "`kind` narrows to one (source | article | event | place | picture | goal | view | point | period); `query` = " +
            "words the bookmark's name or the item's title holds; `limit` (default 200). Use to learn what the person " +
            "cares about. You cannot add, change or remove a bookmark: only the person does, in the web UI or the CLI."
        )
        .input[BookmarksInput]
        .handle(in =>
          personal
            .listBookmarks(Caller.Local, in.kind, in.query, in.limit)
            .fold(McpOut.error, bs => ToolResult.text(PersonalMarkdown.bookmarks(bs)))
        )
    val notes =
      tool("notes")
        .description(
          "READ ONLY: the person's own notes (Markdown), last changed first, each with what it was left on — a source, " +
            "article, event, place, picture, goal, or a point on the map. `id` = one note in full; `kind` and `item` " +
            "(a citekey, slug or id) = the notes left on one item; `query` = words in the note or the item's title; " +
            "`limit` (default 200). Read them as the person's own thoughts, not as sources: never cite a note as " +
            "evidence. You cannot add, change or remove a note: only the person does, in the web UI or the CLI."
        )
        .input[NotesInput]
        .handle { in =>
          in.id.map(_.trim).filter(_.nonEmpty) match
            case Some(id) =>
              personal.note(Caller.Local, id).fold(McpOut.error, n => ToolResult.text(PersonalMarkdown.note(n)))
            case None =>
              personal
                .listNotes(Caller.Local, in.kind, in.item, in.query, in.limit)
                .fold(McpOut.error, ns => ToolResult.text(PersonalMarkdown.notes(ns)))
        }
    List(bookmarks, notes)
