# Skill: Write a PreToolUse Hook

How to build a Rust binary that runs as a Claude Code PreToolUse hook, capable of blocking tool calls (Edit, Write, Bash, etc.) before they execute.

## Reference implementation

`.claude/hooks/check-scala-comments/` — blocks Edit/Write calls that would break Scala comment parity. Read this as a working example.

## Step 1: Scaffold the project

Create the hook directory under `.claude/hooks/<hook-name>/`:

```
.claude/hooks/<hook-name>/
├── Cargo.toml
├── .gitignore    # target/ and logs/
└── src/
    └── main.rs
```

Minimal `Cargo.toml`:
```toml
[package]
name = "<hook-name>"
version = "0.1.0"
edition = "2021"

[dependencies]
serde = { version = "1", features = ["derive"] }
serde_json = "1"

[profile.release]
opt-level = "s"
strip = true
```

## Step 2: Define the input structs

The hook receives JSON on stdin from Claude Code. The exact fields in `tool_input` depend on which tool is being hooked.

**For Edit hooks:**
```rust
#[derive(Deserialize)]
struct HookInput {
    tool_input: ToolInput,
}

#[derive(Deserialize)]
struct ToolInput {
    file_path: Option<String>,
    old_string: Option<String>,
    new_string: Option<String>,
    content: Option<String>,  // Write tool uses this instead of old/new
}
```

**For Bash hooks:**
```rust
#[derive(Deserialize)]
struct ToolInput {
    command: Option<String>,
}
```

The full stdin JSON also includes `session_id`, `cwd`, `hook_event_name`, `tool_name`, and `tool_use_id`, but you only need to deserialize what you use.

## Step 3: Read stdin and parse

```rust
fn main() {
    let mut input = String::new();
    std::io::stdin()
        .read_to_string(&mut input)
        .unwrap_or_else(|e| panic!("Failed to read stdin: {}", e));

    let hook_input: HookInput = serde_json::from_str(&input)
        .unwrap_or_else(|e| panic!("Failed to parse hook input JSON: {}", e));

    // Extract fields from hook_input.tool_input
}
```

## Step 4: Implement your check logic

For Edit/Write hooks that validate file content, compute the **post-edit content** in memory:

```rust
let post_edit_content = if let Some(content) = hook_input.tool_input.content {
    // Write tool: content IS the new file
    content
} else {
    // Edit tool: apply old_string -> new_string to current file
    let current = fs::read_to_string(&file_path)
        .unwrap_or_else(|e| panic!("Failed to read {}: {}", file_path, e));
    let old = hook_input.tool_input.old_string
        .unwrap_or_else(|| panic!("Edit missing old_string for {}", file_path));
    let new = hook_input.tool_input.new_string
        .unwrap_or_else(|| panic!("Edit missing new_string for {}", file_path));
    if !current.contains(&old) {
        panic!("old_string not found in {}", file_path);
    }
    current.replacen(&old, &new, 1)
};
```

Then validate `post_edit_content` against whatever invariant you're checking.

## Step 5: Output the decision

This is the critical part. The hook communicates its decision via **JSON on stdout** and **exit code**.

**To allow the tool call** — exit 0, no output needed:
```rust
process::exit(0);
```

**To block the tool call** — print deny JSON to stdout, exit 2:
```rust
let response = serde_json::json!({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "permissionDecision": "deny",
        "permissionDecisionReason": "Your explanation of why the edit was blocked"
    }
});
println!("{}", response);
process::exit(2);
```

The `permissionDecisionReason` string is shown to Claude, so include enough context for Claude to understand and fix the problem. Include a link to documentation if available.

### What does NOT work

- **stderr only + exit 2**: Claude Code ignores stderr for the decision. You MUST print the JSON to stdout.
- **Exit 2 without JSON**: The edit may still go through. Always pair exit 2 with the deny JSON.
- **`cargo run` in the command**: `cargo run` may swallow the exit code or mix its own stderr with the hook's output. Always use the compiled binary path directly.

## Step 6: Configure in settings.json

Add to `.claude/settings.json` under `PreToolUse`:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "/absolute/path/to/.claude/hooks/<hook-name>/target/release/<hook-name>",
            "timeout": 10000
          }
        ]
      }
    ]
  }
}
```

**Matcher syntax**: `"Edit|Write"` matches both tools. Use `"Bash"` for bash hooks. Separate multiple tools with `|`.

**Timeout**: In milliseconds. 10000 (10s) is generous for a Rust binary. Most checks complete in <200ms.

**Important**: Settings are loaded at session start. After changing settings.json, the user must restart the Claude Code session.

## Step 7: Build

```bash
cd .claude/hooks/<hook-name> && cargo build --release
```

The binary lands at `.claude/hooks/<hook-name>/target/release/<hook-name>`.

## Step 8: Test manually

Test outside of Claude Code first by piping JSON to stdin:

```bash
# Test an edit that should be ALLOWED:
echo '{"tool_input":{"file_path":"/path/to/file.rs","old_string":"good","new_string":"also good"}}' \
  | CLAUDE_PROJECT_DIR=/path/to/project \
  /path/to/.claude/hooks/<hook-name>/target/release/<hook-name>
echo "Exit: $?"
# Should print nothing, exit 0

# Test an edit that should be BLOCKED:
echo '{"tool_input":{"file_path":"/path/to/file.rs","old_string":"good","new_string":"bad"}}' \
  | CLAUDE_PROJECT_DIR=/path/to/project \
  /path/to/.claude/hooks/<hook-name>/target/release/<hook-name>
echo "Exit: $?"
# Should print deny JSON to stdout, exit 2
```

## Step 9: Test live

Ask the user to restart their Claude Code session, then attempt an edit that should be blocked. The hook should reject it and Claude should see the `permissionDecisionReason` in the error message.

## Environment variables

- `CLAUDE_PROJECT_DIR` — Set by Claude Code to the project root. Use this for path resolution.

## Logging

For debugging, write logs to `env!("CARGO_MANIFEST_DIR")/logs/`:

```rust
fn open_log() -> fs::File {
    let log_dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("logs");
    fs::create_dir_all(&log_dir).unwrap();
    let timestamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_millis();
    fs::File::create(log_dir.join(format!("log-{}.log", timestamp))).unwrap()
}
```

Add `logs/` to `.gitignore`. `env!("CARGO_MANIFEST_DIR")` is baked in at compile time — no hardcoded paths needed.

## Common pitfalls

1. **Forgetting the JSON on stdout**: Exit 2 alone doesn't block. You need the `permissionDecision: "deny"` JSON.
2. **Using `cargo run` in settings.json**: It mixes cargo's own output with your hook's stdout. Use the binary directly.
3. **Redirecting stderr to stdout (`2>&1`)**: Your error messages get mixed into the JSON stream. Don't do this.
4. **Not restarting the session**: Settings.json changes only take effect on session restart.
5. **Silent fallbacks**: Per FFFLX, panic on unexpected conditions. Don't silently allow on error.
