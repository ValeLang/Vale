---
g_read_when: "Read when building or testing FrontendRust."
g_auto_load_when_editing:
  - FrontendRust/src/**/*.rs
---

# FrontendRust Build & Test

## Build

```bash
cargo build --manifest-path FrontendRust/Cargo.toml --lib
```

## Run All Tests

```bash
cargo test --manifest-path FrontendRust/Cargo.toml --lib
```

## Run Specific Test Module

```bash
cargo test --manifest-path FrontendRust/Cargo.toml --lib postparsing::test::post_parser_tests
```

Replace `postparsing::test::post_parser_tests` with the desired test module path.
