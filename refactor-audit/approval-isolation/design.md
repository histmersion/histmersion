# Source proposal isolation: required repair scope

This is one cross-feature design problem, not 32 unrelated edits. ApprovalService's
record/proposedBefore methods expose library Source.Status and Edge.Kind. Approvals
forwards them and AgentRepo serializes them. The library's SourceProposals and
CurrencyCheckPipeline are the production producers. Existing contract tests exercise
both database and in-memory implementations through the same service contract.

The intended ownership is library-owned source proposal semantics and engine-owned
approval lifecycle. Move status/edge-specific identity, deduplication and application
into the library contribution. The engine contract must retain typed approval identity,
decision state and payload boundaries without importing library models. Do not erase
Source.Status/Edge.Kind into raw strings, introduce renamed copies solely to hide the
dependency, or simply move ApprovalService wholesale into library: its run-step and
subject approvals also serve other domains.

The rewrite scope includes definitions, implementations, forwarding methods, production
callers, composition and the paired test contracts. `Inventory.scala` extracts the
existing resolved definition/caller evidence and ASTs; these are the input to the
rewrite plan, not a claim that a completed replacement has been chosen.

The inventory selects methods by resolved declaring owner and declaration name, then
uses their actual symbols for callers. This includes AgentRepo's overloaded record
methods; the agent-step overload is a retained negative-control candidate and must
not be rewritten as source-proposal handling. The first exploratory exact-symbol
selection missed that overload; the corrected scope is 10 definitions and 10 resolved
reference rows across eight files. No application rewrite used the incomplete selection.

Behavioral obligations to preserve:

- An identical open source proposal is inserted once; a decided proposal may be
  proposed again. The database enforces this with approvals_open_once over kind,
  citekey and coalesced status/edge_kind/target, filtered to open rows.
- proposedBefore searches all decision states using the same source identity.
- Automatically applied proposals retain result and decided timestamp. Ordinary
  open proposals do not acquire a decided timestamp.
- Run-step opening remains unique by job_id/seq; subject proposals deliberately
  are not deduplicated as source proposals are.
- Decisions remain compare-and-set; pages, facets, payload decoding and source
  proposal responses retain the behavior asserted by ApprovalServiceContract.
- Transaction ownership remains in the service orchestration; repositories receive
  a connection and do not start transactions.

Before application, freeze expected AST output and run both existing contract
implementations as a baseline. Generate a complete preview using Scalafix, compile
definitions and callers, run the identical contracts and remeasure the retained
relationship IDs. Any required schema change needs an additive migration and a data
compatibility test; do not edit the existing V109 schema migration. This coupling
remains outstanding required work, not an accepted exception or boy-scout residual.
