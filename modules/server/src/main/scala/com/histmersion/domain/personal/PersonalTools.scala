package com.histmersion.domain.personal

import chimp.server.*
import com.histmersion.api.personal.PersonalMarkdown
import com.histmersion.server.{McpOut, McpTools}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The person's bookmarks and notes for agents to READ: there is no tool that makes, changes or removes one — those are
  * the person's own, written in the web UI and with `histmersion bookmarks|notes`.
  */
object PersonalTools:
  import McpTools.given

  final case class BookmarksInput(kind: Option[String] = None, query: Option[String] = None, limit: Option[Int] = None)
      derives KindlingsCodecAsObject
  object BookmarksInput:
    given Schema[BookmarksInput] = KindlingsSchema.derived[BookmarksInput].schema

  final case class NotesInput(
      id: Option[String] = None,
      kind: Option[String] = None,
      item: Option[String] = None,
      query: Option[String] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object NotesInput:
    given Schema[NotesInput] = KindlingsSchema.derived[NotesInput].schema

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
            .listBookmarks(in.kind, in.query, in.limit)
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
            case Some(id) => personal.note(id).fold(McpOut.error, n => ToolResult.text(PersonalMarkdown.note(n)))
            case None     =>
              personal
                .listNotes(in.kind, in.item, in.query, in.limit)
                .fold(McpOut.error, ns => ToolResult.text(PersonalMarkdown.notes(ns)))
        }
    List(bookmarks, notes)
