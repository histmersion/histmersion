package com.histmersion
package domain.library

import com.histmersion.api.Api
import com.histmersion.api.library.{Citekey as ApiCitekey, Facet, Filter, Source}
import com.histmersion.domain.library.citations.{CitationsControllers, CitationService}
import com.histmersion.domain.library.findings.FindingsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.{FilterSql, Search, SearchControllers}
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint
import com.histmersion.domain.Creator
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{tolerated, LintRule}

/** The library's endpoints bound to its features' controllers, in the order route matching needs (`sources/{ck}/lint`
  * and `…/view` before `sources/{ck}`, `sources/{ck}/export` before the write paths). The endpoints of other owners
  * that sit between them keep their places: the articles citing a source, Markdown checked before it is stored, an id
  * resolved to a source or an article, the seed, a source's note, the chunks' rebuild.
  */
object LibraryRoutes:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def apply(
      search: SearchControllers,
      labels: LabelsControllers,
      sources: SourcesControllers,
      findings: FindingsControllers,
      citations: CitationsControllers,
      sourceArticles: ServerEndpoint[Any, Identity],
      lintMarkdown: ServerEndpoint[Any, Identity],
      resolveId: ServerEndpoint[Any, Identity],
      seed: ServerEndpoint[Any, Identity],
      exportSource: ServerEndpoint[Any, Identity],
      reindex: ServerEndpoint[Any, Identity]
  ): List[ServerEndpoint[Any, Identity]] =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
    def citekey(ck: ApiCitekey): Either[Api.Error, Citekey] =
      sources.parseCitekey(ck)
    def citekeyOpt(ck: Option[ApiCitekey]): Either[Api.Error, Option[Citekey]] =
      ck.fold[Either[Api.Error, Option[Citekey]]](Right(None))(c => citekey(c).map(Some(_)))
    List(
      Api.search.serverLogicSuccess[Identity]((q, limit) =>
        search.search(q.map(t => Search.Query(t.value)), limit.map(l => Filter.Limit(l.value)))
      ),
      Api.sources.serverLogicSuccess[Identity](f => search.sources(f)),
      Api.facets.serverLogic[Identity]((field, prefix, f) =>
        labels
          .facets(Facet.Field.Wire(field.value), prefix.map(p => FilterSql.FacetPrefix(p.value)), f)
          .left
          .map(Api.Error(_))
      ),
      sourceArticles,
      Api.lintSource.serverLogic[Identity](ck => citekey(ck).flatMap(sources.lint)),
      Api.fixSourceFormat.serverLogic[Identity](ck => citekey(ck).flatMap(sources.fix)),
      lintMarkdown,
      Api.sourceView.serverLogic[Identity](q =>
        citekey(q.citekey).flatMap(ck =>
          sources
            .markdown(
              ck,
              SourcesControllers.MarkdownFullText(q.fullText.value),
              q.section,
              q.heading.map(h => SourceService.Heading(h.value)),
              q.offset.map(o => Offset.option(o.value).getOrElse(Offset.Start)),
              q.chars.map(c => SourcesControllers.Chars(c.value))
            )
            .left
            .map(Api.Error(_))
            .map(md => Source.View(md.value))
        )
      ),
      Api.passages.serverLogic[Identity](q => sources.passages(q)),
      resolveId,
      Api.getSource.serverLogic[Identity]((ck, body) =>
        citekey(ck).flatMap(sources.get(_, SourcesControllers.GetBody(body.value)).left.map(Api.Error(_)))
      ),
      Api.findingsPage.serverLogicSuccess[Identity](q => findings.page(q)),
      Api.findings.serverLogicSuccess[Identity](q => findings.feed(q)),
      Api.aggregate.serverLogic[Identity](q => search.aggregate(q)),
      Api.neighbors.serverLogic[Identity]((ck, kinds) =>
        citekey(ck).map(citations.neighbors(_, kinds.map(k => Edge.Kind.Wire(k.value))))
      ),
      Api.graph.serverLogic[Identity]((ck, depth) =>
        citekey(ck).map(citations.graph(_, depth.map(d => CitationService.Depth(d.value))))
      ),
      Api.contradictions.serverLogic[Identity]((ck, since) =>
        citekeyOpt(ck).map(citations.contradictions(_, since.map(a => CitationService.Since(a.value))))
      ),
      seed,
      exportSource,
      Api.putSource.serverLogic[Identity]((ck, in, strict) =>
        CreatorControllers.rest(in)(citekey(ck).flatMap(sources.put(_, in, SourcesControllers.PutStrict(strict.value))))
      ),
      Api.patchSource.serverLogic[Identity]((ck, p, strict) =>
        CreatorControllers.rest(p) {
          citekey(ck).flatMap(sources.patch(_, p.patch, SourcesControllers.PatchStrict(strict.value)))
        }
      ),
      Api.renameSource.serverLogic[Identity]((ck, req) =>
        CreatorControllers.rest(req)(citekey(ck).flatMap(sources.rename(_, req)))
      ),
      Api.deleteSource.serverLogic[Identity]((ck, req) => citekey(ck).flatMap(sources.delete(_, req))),
      Api.sourceAliases.serverLogicSuccess[Identity](_ => sources.aliases),
      Api.importNote.serverLogic[Identity](req => CreatorControllers.rest(req)(sources.importNote(req))),
      Api.citekeys.serverLogicSuccess[Identity](_ => sources.citekeys.map(c => ApiCitekey(c.value))),
      Api.resolveEdges.serverLogic[Identity](_ => citations.resolveEdges),
      reindex
    )
