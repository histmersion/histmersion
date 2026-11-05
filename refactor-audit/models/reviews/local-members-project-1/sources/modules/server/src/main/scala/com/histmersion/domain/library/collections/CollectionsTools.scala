package com.histmersion.domain.library.collections

import chimp.server.*
import com.histmersion.api.library.*
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.{
  CollectionFieldValue,
  CollectionImportRequest,
  CollectionLinkRequest,
  CollectionPushRequest,
  CollectionSetRequest,
  CollectionsMarkdown,
  CollectionUpdateOne
}

/** The collections as one MCP tool, `collection`: the same controllers the REST routes call, every answer Markdown. */
object CollectionsTools:
  type ApplyAnswerFResult = ApplyAnswerFResult.T
  object ApplyAnswerFResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import com.histmersion.util.mcp.ToolInputs.given

  final case class CollectionInput(
      action: CollectionInput.Action,
      name: Option[CollectionInput.Name] = None,
      query: Option[CollectionInput.Query] = None,
      id: Option[CollectionInput.Id] = None,
      citekey: Option[com.histmersion.domain.library.Citekey] = None,
      format: Option[CollectionInput.Format] = None,
      limit: Option[CollectionInput.Limit] = None,
      unlinked: CollectionInput.Unlinked = false,
      convert: CollectionInput.Convert = true,
      replace: CollectionInput.Replace = false,
      apply: CollectionInput.Apply = false,
      fields: List[CollectionInput.Fields] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object CollectionInput:
    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Format = Format.T
    object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unlinked = Unlinked.T
    object Unlinked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Convert = Convert.T
    object Convert extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Replace = Replace.T
    object Replace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Apply = Apply.T
    object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fields = Fields.T
    object Fields extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def apply(c: CollectionsControllers) =
    val me = Caller.Local
    def answer[A](r: Either[com.histmersion.api.Api.Error, A])(f: A => com.histmersion.domain.library.collections.CollectionsTools.ApplyAnswerFResult) =
      ToolResult.text(r.fold(McpOut.refusal, f))
    val collection =
      tool("collection")
        .description(
          "The library's SOURCE COLLECTIONS — the user's Calibre library (`name`: calibre, the default) and archive.org " +
            "(archive-org; use the `archive` tool for it). `action`: `list` (the collections and whether the server " +
            "reaches them) · `search` (`query`, `limit`; `unlinked: true` = the entries no source links) · `import` " +
            "(`id` into the corpus; `citekey`, `format`, `convert: false` = the record only) · `link` (`citekey` to " +
            "entry `id`) · `push` (`citekey`'s stored original as a new entry; `replace: true` swaps the linked " +
            "entry's file) · `update` (`citekey`'s entry from its record; without `citekey`, every linked entry — a " +
            "dry run unless `apply: true`) · `set` (`id`, `fields` as `name:value`) · `audit` · `gaps` (sources with a " +
            "fetched file not in Calibre) · `sync` (push the gaps; a dry run unless `apply: true`). Writes need the " +
            "Calibre GUI closed."
        )
        .input[CollectionInput]
        .handle { in =>
          val name = in.name.map(_.trim).filter(_.nonEmpty).getOrElse(CollectionsService.Calibre)
          in.action.trim.toLowerCase(java.util.Locale.ROOT) match
            case "list"   => answer(c.collections(me))(CollectionsMarkdown.collections)
            case "info"   => answer(c.collection(me, name))(CollectionsMarkdown.collection)
            case "search" =>
              answer(c.search(me, name, in.query, in.limit, in.unlinked))(s =>
                if in.unlinked then CollectionsMarkdown.unlinked(s.entries, s.total)
                else CollectionsMarkdown.search(s.entries)
              )
            case "import" =>
              in.id.fold(ToolResult.text("_give the entry's `id`_"))(id =>
                answer(c.importItem(me, name, CollectionImportRequest(id, in.citekey, in.format, in.convert)))(
                  CollectionsMarkdown.imported
                )
              )
            case "link" =>
              (in.citekey, in.id) match
                case (Some(ck), Some(id)) =>
                  answer(c.link(me, name, CollectionLinkRequest(ck, id)))(CollectionsMarkdown.linked)
                case _ => ToolResult.text("_give a `citekey` and the entry's `id`_")
            case "push" =>
              in.citekey.fold(ToolResult.text("_give a `citekey`_"))(ck =>
                answer(c.push(me, name, CollectionPushRequest(ck, in.replace)))(CollectionsMarkdown.pushed)
              )
            case "update" =>
              in.citekey match
                case Some(ck) => answer(c.updateOne(me, name, CollectionUpdateOne(ck)))(CollectionsMarkdown.updatedOne)
                case None     =>
                  answer(c.updateAll(me, name, CollectionUpdateAll.Request(in.apply)))(u =>
                    CollectionsMarkdown.updateAll(u.rows, u.applied)
                  )
            case "set" =>
              val fields = in.fields.flatMap(f =>
                f.split(":", 2) match
                  case Array(k, v) if k.trim.nonEmpty => Some(CollectionFieldValue(k.trim, v))
                  case _                              => None
              )
              in.id.fold(ToolResult.text("_give the entry's `id`_"))(id =>
                answer(c.set(me, name, CollectionSetRequest(id, fields)))(CollectionsMarkdown.set)
              )
            case "audit" => answer(c.audit(me, name))(CollectionsMarkdown.audit)
            case "gaps"  => answer(c.gaps(me, name, in.limit))(g => CollectionsMarkdown.gaps(g.gaps))
            case "sync"  =>
              answer(c.sync(me, name, CollectionSync.Request(in.apply, in.limit)))(CollectionsMarkdown.sync)
            case other =>
              ToolResult.text(
                s"_unknown action '$other' — list, info, search, import, link, push, update, set, audit, gaps, sync_"
              )
        }
    List(collection)
