package com.histmersion
package domain.library.collections
import com.histmersion.util.mcp.ChimpJson.given

import com.histmersion.api.json.NewtypeCodecs.given
import chimp.server.*
import com.histmersion.api.library.*
import com.histmersion.domain.Caller
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.problem.Problem
import com.histmersion.util.json.JsonCodec
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
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.domain.Creator
import com.histmersion.api.automation.Creation
import com.histmersion.domain.CreatorControllers

/** The collections as one MCP tool, `collection`: the same controllers the REST routes call, every answer Markdown. */
object CollectionsTools:
  import com.histmersion.util.mcp.ToolInputs.given

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class CollectionInput(
      action: CollectionInput.Action,
      name: Option[CollectionInput.Name] = None,
      query: Option[CollectionInput.Query] = None,
      id: Option[CollectionInput.Id] = None,
      citekey: Option[Citekey] = None,
      format: Option[CollectionInput.Format] = None,
      limit: Option[CollectionInput.Limit] = None,
      unlinked: CollectionInput.Unlinked = CollectionInput.Unlinked(false),
      convert: CollectionInput.Convert = CollectionInput.Convert(true),
      replace: CollectionInput.Replace = CollectionInput.Replace(false),
      apply: CollectionInput.Apply = CollectionInput.Apply(false),
      fields: List[CollectionInput.Fields] = Nil,
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  object CollectionInput:
    type Action = Action.T
    object Action extends Newtype[String, Pure]
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    export domain.library.search.Search.Query
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Format = Format.T
    object Format extends Newtype[String, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
    type Unlinked = Unlinked.T
    object Unlinked extends Newtype[Boolean, Pure]
    type Convert = Convert.T
    object Convert extends Newtype[Boolean, Pure]
    type Replace = Replace.T
    object Replace extends Newtype[Boolean, Pure]
    type Apply = Apply.T
    object Apply extends Newtype[Boolean, Pure]
    type Fields = Fields.T
    object Fields extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def apply(c: CollectionsControllers) =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Mcp)
    val me = Caller.Local
    def answer[A](r: Either[Api.Error, A])(f: A => String) = McpOut.of(r)(f)
    val noId = McpOut.missing(Problem.Field("id"))
    val noCitekey = McpOut.missing(Problem.Field("citekey"))
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
          CreatorControllers.mcp(in) {
            val name = in.name
              .map(_.value.trim)
              .filter(_.nonEmpty)
              .fold(CollectionsService.Calibre)(CollectionsService.CollectionName(_))
            val limit = in.limit.map(l => CollectionsService.Limit(l.value))
            in.action.value.trim.toLowerCase(java.util.Locale.ROOT) match
              case "list"   => answer(c.collections(me).left.map(Api.Error(_)))(CollectionsMarkdown.collections)
              case "info"   => answer(c.collection(me, name))(CollectionsMarkdown.collection)
              case "search" =>
                answer(c.search(me, name, in.query, limit, CollectionsService.SearchUnlinked(in.unlinked.value)))(s =>
                  if in.unlinked.value then
                    CollectionsMarkdown.unlinked(s.entries, CollectionsMarkdown.Total(s.total.value))
                  else CollectionsMarkdown.search(s.entries)
                )
              case "import" =>
                in.id.fold(noId)(id =>
                  answer(
                    c.importItem(
                      me,
                      name,
                      CollectionImportRequest(
                        CollectionImportRequest.Id(id.value),
                        in.citekey,
                        in.format.map(f => CollectionImportRequest.Format(f.value)),
                        CollectionImportRequest.Convert(in.convert.value)
                      )
                    )
                  )(
                    CollectionsMarkdown.imported
                  )
                )
              case "link" =>
                (in.citekey, in.id) match
                  case (Some(ck), Some(id)) =>
                    answer(c.link(me, name, CollectionLinkRequest(ck, CollectionLinkRequest.Id(id.value))))(
                      CollectionsMarkdown.linked
                    )
                  case (None, _) => noCitekey
                  case _         => noId
              case "push" =>
                in.citekey.fold(noCitekey)(ck =>
                  answer(c.push(me, name, CollectionPushRequest(ck, CollectionPushRequest.Replace(in.replace.value))))(
                    CollectionsMarkdown.pushed
                  )
                )
              case "update" =>
                in.citekey match
                  case Some(ck) =>
                    answer(c.updateOne(me, name, CollectionUpdateOne(ck)))(CollectionsMarkdown.updatedOne)
                  case None =>
                    answer(
                      c.updateAll(
                        me,
                        name,
                        CollectionUpdateAll.Request(CollectionUpdateAll.Request.Apply(in.apply.value))
                      )
                    )(u => CollectionsMarkdown.updateAll(u.rows, u.applied.value))
              case "set" =>
                val fields = in.fields.flatMap(f =>
                  f.value.split(":", 2) match
                    case Array(k, v) if k.trim.nonEmpty =>
                      Some(CollectionFieldValue(CollectionFieldValue.Name(k.trim), CollectionFieldValue.Value(v)))
                    case _ => None
                )
                in.id.fold(noId)(id =>
                  answer(c.set(me, name, CollectionSetRequest(CollectionSetRequest.Id(id.value), fields)))(
                    CollectionsMarkdown.set
                  )
                )
              case "audit" => answer(c.audit(me, name))(CollectionsMarkdown.audit)
              case "gaps"  => answer(c.gaps(me, name, limit))(g => CollectionsMarkdown.gaps(g.gaps))
              case "sync"  =>
                answer(
                  c.sync(
                    me,
                    name,
                    CollectionSync.Request(
                      CollectionSync.Request.Apply(in.apply.value),
                      in.limit.map(l => CollectionSync.Request.Limit(l.value))
                    )
                  )
                )(CollectionsMarkdown.sync)
              case other =>
                McpOut.unknown(
                  Problem.TypeName("CollectionAction"),
                  other,
                  Problem.Raw("list"),
                  Problem.Raw("info"),
                  Problem.Raw("search"),
                  Problem.Raw("import"),
                  Problem.Raw("link"),
                  Problem.Raw("push"),
                  Problem.Raw("update"),
                  Problem.Raw("set"),
                  Problem.Raw("audit"),
                  Problem.Raw("gaps"),
                  Problem.Raw("sync")
                )
          }
        }
    List(collection)
