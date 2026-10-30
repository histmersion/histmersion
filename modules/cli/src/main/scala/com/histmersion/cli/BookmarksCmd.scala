package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.personal.*

/** `histmersion bookmarks list|add|rm` — your bookmarks: items of the collection, places on the map, saved
  * views of Explore and periods. */
object BookmarksCmd:

  private val list = Opts.subcommand("list", "Your bookmarks, newest first") {
    (Opts.option[String]("kind", s"Only: ${ItemKind.values.map(_.wire).mkString(", ")}").orNone,
      Opts.option[String]("query", "Words the bookmark's name or the item's title holds").orNone,
      Opts.option[Int]("limit", s"How many (default ${PersonalIds.DefaultLimit})").orNone,
      Output.jsonFlag).mapN { (kind, q, limit, json) => (c: Client) =>
      c.call(PersonalApi.bookmarks)((kind, q, limit)).map(bs => Output.emit(json, bs)(PersonalMarkdown.bookmarks))
    }
  }

  private val add = Opts.subcommand("add", "Bookmark an item (`source anthony2007_horse`), a place on the map " +
    "(`point --lat 37.73 --lon 22.75`), a view (`view --view 'from=-1300&to=-1100' --lat .. --lon .. --zoom 6`) or a " +
    "period (`period --from -1200 --to -1150 --label 'the crisis years'`)") {
    (TargetFlags.opts(ItemKind.Bookmarkable, views = true),
      Opts.option[String]("label", "Your name for it").orNone,
      Opts.option[String]("title", "What the item is called, when the collection does not hold it").orNone,
      Output.jsonFlag).mapN { (raw, label, title, json) => (c: Client) =>
      TargetFlags.target(raw).flatMap(t =>
        c.call(PersonalApi.addBookmark)(BookmarkNew(t, label.map(BookmarkLabel(_)), title))
          .map(b => Output.emit(json, b)(b => s"Bookmarked: ${PersonalMarkdown.bookmarkLine(b).stripPrefix("- ")}")))
    }
  }

  private val rm = Opts.subcommand("rm", "Remove a bookmark (its id, from `bookmarks list`)") {
    (Opts.argument[String]("id"), Output.jsonFlag).mapN { (id, json) => (c: Client) =>
      c.call(PersonalApi.removeBookmark)(id).map(b => Output.emit(json, b)(b => s"Removed the bookmark **${b.name}**."))
    }
  }

  val command: Opts[Client => Either[CliError, Unit]] =
    Opts.subcommand("bookmarks", "Your bookmarks: list, add, rm")(list orElse add orElse rm)
