package com.histmersion
package cli
import com.histmersion.api.json.NewtypeCodecs.given

import com.histmersion.api.personal.*
import com.histmersion.api.ItemKind
import com.histmersion.util.console.Console
import com.histmersion.api.library.Title

/** `histmersion bookmarks list|add|rm` — your bookmarks: items of the collection, places on the map, saved views of
  * Explore and periods.
  */
object BookmarksCmd:

  /** What `histmersion …` of this group was asked to do, every argument parsed. */
  enum Command extends Cli.Command:
    case ListCmd(kind: Option[String], q: Option[String], limit: Option[Int], json: Cli.JsonOutput)
    case Add(raw: TargetFlags.Raw, label: Option[String], title: Option[String], json: Cli.JsonOutput)
    case Rm(id: String, json: Cli.JsonOutput)

    def run(c: Client)(using Console): Either[Cli.Error, Unit] = Perform(c)(this)

  /** Carries out Command commands against one server. */
  final private[cli] class Perform(c: Client)(using console: Console):
    def apply(cmd: Command): Either[Cli.Error, Unit] =
      cmd match
        case Command.ListCmd(kind, q, limit, json) =>
          c.call(PersonalApi.bookmarks)(
            (kind.map(ItemKind.Wire(_)), q.map(PersonalApi.Search(_)), limit)
          ).map(bs => Output.emit(json, bs)(PersonalMarkdown.bookmarks))
        case Command.Add(raw, label, title, json) =>
          TargetFlags
            .target(raw)
            .flatMap(t =>
              c.call(PersonalApi.addBookmark)(Bookmark.New(t, label.map(Bookmark.Label(_)), title.map(Title(_))))
                .map(b =>
                  Output.emit(json, b)(b => s"Bookmarked: ${PersonalMarkdown.bookmarkLine(b).stripPrefix("- ")}")
                )
            )
        case Command.Rm(id, json) =>
          c.call(PersonalApi.removeBookmark)(Bookmark.Id(id))
            .map(b => Output.emit(json, b)(b => s"Removed the bookmark **${b.name}**."))
