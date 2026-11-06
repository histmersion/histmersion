# Second pass: are the kept newtypes real concepts?

Histmersion's Scala code was wrapped in newtypes by a tool. A first pass merged method-only types. These remain, mostly on
record fields. The user wants one concept, one type; different purposes, different types. A record's field that repeats
another entity's property, or a parameter's copy of a concept, should use the concept's own type.

Output one line per type, tab-separated, every type exactly once, nothing else:

    <fqn>	<decision>

Decisions:
- `keep`: the default. It is the owner's own property: `Bookmark.Id`, `Source.Title`, `Goal.Slug`, `Place.Kind`, a
  query's own parameter with its own meaning. Most `<Entity>.<Property>` types stay.
- `<fqn of an existing concept>`: it is the same thing as a type listed under CONCEPTS NEARBY, or in concepts-new.txt, with the
  SAME base type. Use this when the owner is a query, request, response, row, view, renderer or helper object, and the name repeats a
  concept that belongs to an entity (`AggregateQuery.AsOf` and `PlacesGeoQuery.AsOf` are one concept; a `GoalLink.Slug` is the
  goal's slug; `Markdown.PeriodStart` is a period's start).
- `new:<Owner>.<Name>`: a real concept with no type yet, when this type's name is positional or describes where it is used
  (`GeometryTypesValue`, `AppliedCount`, `OfSubjectResult`, anything ending Result, Part1, Arg1, S, Raw, Value, Str). Owner is
  the entity or feature object owning it; Name is a short noun for its purpose.
- `native`: plain type. ONLY for a Boolean predicate's result, or a private helper's text or number with no domain meaning.

Rules: never map across base types, and never from `com.histmersion.api` to `com.histmersion.domain`. Never use
`com.histmersion.api.Markdown.Text` except for the complete Markdown returned by a renderer. An error message is
`new:<Owner>.Problem`. Counts, limits, offsets, ids and years stay distinct from each other. When unsure, `keep`.
