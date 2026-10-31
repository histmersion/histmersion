package com.histmersion.util.data

/** Today's owners and their tables, in one table until each owner registers its own. */
object TablesAdapter:
  val AutomationOwner: DataOwner = DataOwner("automation")
  val CatalogueOwner: DataOwner = DataOwner("catalogue")
  val MediaOwner: DataOwner = DataOwner("media")
  val LibraryOwner: DataOwner = DataOwner("library")
  val ResearchOwner: DataOwner = DataOwner("research")
  val PersonalOwner: DataOwner = DataOwner("personal")
  val DataArea: DataOwner = DataOwner("util.data")
  val JobsOwner: DataOwner = DataOwner("util.jobs")
  val IdentityOwner: DataOwner = DataOwner("util.identity")
  val DatabaseOwner: DataOwner = DataOwner("util.database")

  /** The order every operation runs the owners in: the domains bottom up, personal, then the supporting areas. */
  val owners: List[DataOwner] = List(AutomationOwner, CatalogueOwner, MediaOwner, LibraryOwner, ResearchOwner, PersonalOwner, DataArea, JobsOwner, IdentityOwner, DatabaseOwner)

  def of(owner: DataOwner): List[OwnedTable] = owner match
    case AutomationOwner =>
      OwnedTable.all(
        "agent_allowances", "agent_autopilot", "agent_leases", "agent_managers", "agent_messages",
        "agent_role_chains", "agent_role_fallthrough", "agent_state", "agent_steps", "approvals", "manager_events",
        "manager_hint_uses", "manager_hints", "manager_items", "plan_pools", "plan_quota_readings",
        "plan_quota_refreshes", "goals", "goal_aliases", "goal_focus", "goal_links", "goal_schedules", "queue_items"
      )
    case CatalogueOwner =>
      OwnedTable.all(
        "places", "place_aliases", "place_descriptions", "place_footprints", "place_links", "place_names",
        "place_overrides", "place_relations", "place_removals", "place_types", "gazetteer_sources", "land",
        "periods", "seed_versions", "events", "event_aliases", "event_catalogue", "event_datings", "event_edges",
        "event_import_parts", "event_link_candidates", "event_link_checks", "event_participants", "corrections",
        "attestations", "site_undated_build", "site_undated_cells", "map_changes"
      )
    case MediaOwner => OwnedTable.all("assets", "asset_links", "media_links", "media_checks")
    case LibraryOwner =>
      OwnedTable.all(
        "sources", "source_aliases", "source_candidates", "source_chunks", "source_identifiers", "findings",
        "finding_places", "edges", "unresolved_edges", "assessments", "retraction_watch", "candidate_watches",
        "acquire_checks", "upstream_quota"
      )
    case ResearchOwner =>
      OwnedTable.all(
        "articles", "article_aliases", "article_critique_reasons", "article_links", "article_versions",
        "review_ledgers"
      )
    case PersonalOwner => OwnedTable.all("bookmarks", "notes", "read_marks", "ui_seen")
    case DataArea => OwnedTable.all("merges", "merge_decisions", "merge_ledger", "merge_sessions")
    case JobsOwner => OwnedTable.all("jobs")
    case IdentityOwner => OwnedTable.all("instance", "uid_aliases", "tombstones", "alias_tombstones")
    case DatabaseOwner => OwnedTable.all("flyway_schema_history", "spatial_ref_sys")
    case _        => Nil
