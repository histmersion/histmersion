# Deciding what each generated newtype really is

A tool wrapped primitives in Histmersion's Scala code with one type per position (`ClassifyRaw`, `ParseMachine`,
`DescribeResult`, `ActivityPart1Part2`). The user wants: one concept, one type; different purposes, different types;
a function's meaning carried by a real concept, not by its position. For each listed type output one decision.

Output one line per type, tab-separated, every type exactly once, nothing else:

    <fqn>	<decision>

Decisions:
- `<fqn of an existing concept>`: it is the same thing as a type under CONCEPTS NEARBY or in kernel.txt, with the SAME
  base type. Names of a parameter or result that receive or return a model field are that field's type.
- `new:<Owner>.<Name>`: a real concept with no type yet. Owner is the entity or feature object that owns it (an fqn
  from the type's own package); Name is a short noun for the purpose (`Source.Note`, `api.Markdown.Text`). Use the
  same name for every type in your batch that means the same thing. Do not name by position: no Result, Part1, Arg1,
  S, Raw, Value, Str.
- `native`: use the plain type. ONLY for: a Boolean predicate's result or flag local to a method; a private helper's
  text or number inside the type it serves, such as trimming, splitting, escaping, or arithmetic with no domain meaning;
  a loop counter, index or length; an input to a library call.

Rules: never map across different base types. A type in `com.histmersion.api` may only map to `com.histmersion.api...` or
`new:com.histmersion.api...`, never to `com.histmersion.domain...`: the API layer has its own unconstrained copies (for a
citekey, `new:com.histmersion.api.library.Citekey`; for an asset's uid, `new:com.histmersion.api.media.Asset.Uid`, same name
in every batch). A type in `com.histmersion.domain` stays in `com.histmersion.domain` or the kernel. Use
`new:com.histmersion.api.Markdown.Text` ONLY for the complete Markdown document or block returned by a renderer (an object
named `...Markdown`, `...Export`, `...Render`); never for an error message (`new:<Owner>.Problem`), a label, a name, a
symbol or a fragment of a string (`native` if it has no meaning of its own). Wire strings of a vocabulary: that vocabulary's `Wire` if listed, else `new:<Vocabulary>.Wire`.
Counts, limits, offsets, years, ids stay distinct from each other. When unsure between two concepts, prefer `new:`
in the owner over a guess; when the type's use is unknown, decide from its name and owner.
