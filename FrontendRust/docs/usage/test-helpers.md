# Test Helper Conventions

Project-specific conventions for writing FrontendRust tests. General testing shields live in `Luz/shields/` (PSMONMX, NHCITX, UEFIAIX, UCMTRSX, TPUTEFCX, etc.).

## Find by name, don't name-match

Use `find_func_named` / `find_struct_named` (etc.) instead of a `collect_where` that filters on a name. The named finders are clearer and fail loudly when absent.

## Name match-bound string variables specifically

When a pattern binds a variable only to compare it against a literal string, give it a specific name rather than a generic underscore-suffixed one:

```rust
let program = compile("export int as NumberThing;");
collect_only!(&program, NodeRefP::ExportAs(ExportAsP {
    struct_: ITemplexPT::NameOrRune(NameOrRunePT { name: NameP { str: ref int_, .. } }),
    exported_name: NameP { str: ref number_thing_, .. },
    ..
}) if int_.str == "int" && number_thing_.str == "NumberThing" => Some(()));
```

If the string isn't a valid Rust identifier (e.g. `"*"`), make one up (`star_`).
