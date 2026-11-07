# Deciding what each newtype really is (modules cli and web, the clients)

A tool wrapped primitives in Histmersion's Scala code with one type per position (`ClassifyRaw`, `DescribeResult`, `ActivityPart1Part2`).
The user wants one concept, one type within a layer; different purposes, different types. Output one line per type, tab-separated, every type exactly once, nothing else:

    <fqn>	<decision>

Decisions:
- `keep`: a real property of its owner with its own meaning: `Plugin.Id`, `LandingConfig.AllowHosts`. In config, a setting usually stays its own type. Default for `original` types.
- `<fqn>`: the same thing as a type under CONCEPTS NEARBY or in concepts-basic.txt, with the SAME base type.
- `new:<full owner path>.<Name>`: a real concept with no type yet, when the name is positional or says where it is used (ending Result, Part1, Arg1, S, Raw, Value, Str). The owner is the entity or feature object in the type's own package. Give the full path starting `com.histmersion.`. Use the same name for the same meaning.
- `native`: plain type, ONLY for a Boolean predicate's result, a loop counter, or a private helper's text or number with no domain meaning. Never for a record field.

Rules: never map across base types. cli and web see only their own types and the API types (`com.histmersion.api...`) listed in CONCEPTS NEARBY and concepts-basic.txt: map to those, never to `com.histmersion.domain...`. Client types are unconstrained and never validate. A `Part1`/`Result` usually carries one API concept, such as `api.library.Citekey` or a page limit: look there first. Error messages are `new:<Owner>.Problem`. Counts, limits, offsets, ids and years stay distinct.
