use std::io::{self, Read};

const VALID_PREFIXES: &[&str] = &[
    "/// Arena-allocated (see @TFITCX)",
    "/// Value-type (see @TFITCX)",
    "/// Interned (see @TFITCX)",
    "/// Interning transient (see @TFITCX)",
    "/// Polyvalue (see @TFITCX)",
    "/// Temporary state (see @TFITCX)",
    "/// Miscellaneous type (see @TFITCX)",
];

fn strip_diff_prefix(line: &str) -> Option<&str> {
    if line.starts_with("+++") || line.starts_with("---") || line.starts_with("@@") {
        None
    } else if line.starts_with('+') {
        Some(&line[1..])
    } else if line.starts_with('-') {
        None // skip removed lines
    } else {
        Some(line) // context line
    }
}

fn main() {
    let mut input = String::new();
    io::stdin().read_to_string(&mut input).expect("failed to read stdin");

    let lines: Vec<&str> = input.lines().collect();
    let mut violations = Vec::new();
    let mut in_block_comment = false;
    let mut in_test_module = false;

    for i in 0..lines.len() {
        let content = match strip_diff_prefix(lines[i]) {
            Some(c) => c,
            None => continue,
        };
        let trimmed = content.trim();

        // Track block comments (Scala code in /* ... */)
        if !in_block_comment && trimmed.starts_with("/*") {
            if !trimmed.contains("*/") || trimmed.ends_with("*/") && trimmed.starts_with("/*") {
                // Single-line block comments like /* Guardian: disable-all */ are fine
                if !trimmed.ends_with("*/") {
                    in_block_comment = true;
                }
            }
            continue;
        }
        if in_block_comment {
            if trimmed.contains("*/") {
                in_block_comment = false;
            }
            continue;
        }

        // Track #[cfg(test)] modules
        if trimmed == "#[cfg(test)]" {
            in_test_module = true;
            continue;
        }
        if in_test_module {
            continue;
        }

        // Check for struct or enum definitions
        let is_struct = trimmed.starts_with("pub struct ") || trimmed.starts_with("struct ");
        let is_enum = trimmed.starts_with("pub enum ") || trimmed.starts_with("enum ");

        if is_struct || is_enum {
            // Scan backwards past #[...] attributes, blank lines, and // comments
            // to find the category doc comment
            let has_category = scan_backwards_for_category(&lines, i);

            if !has_category {
                let kind = if is_struct { "struct" } else { "enum" };
                let name = trimmed
                    .trim_start_matches("pub ")
                    .trim_start_matches("struct ")
                    .trim_start_matches("enum ")
                    .split(|c: char| c == '<' || c == '{' || c == '(' || c.is_whitespace())
                    .next()
                    .unwrap_or("unknown");
                violations.push(format!(
                    "{} `{}` missing category annotation (expected one of: /// Arena-allocated, /// Value-type, /// Interned, /// Interning transient, /// Polyvalue, /// Temporary state, /// Miscellaneous type)",
                    kind, name
                ));
            }
        }
    }

    if violations.is_empty() {
        println!("{{\"violations\":[]}}");
    } else {
        let result = serde_json::json!({
            "violations": violations.iter()
                .map(|r| serde_json::json!({"reason": r}))
                .collect::<Vec<_>>()
        });
        println!("{}", result);
    }
}

/// Scan backwards from a struct/enum definition line, skipping #[...] attributes,
/// blank lines, and regular // comments, looking for a TFITCX category doc comment.
fn scan_backwards_for_category(lines: &[&str], start: usize) -> bool {
    let mut j = start;
    while j > 0 {
        j -= 1;
        let prev_content = match strip_diff_prefix(lines[j]) {
            Some(c) => c,
            None => continue, // skip removed lines
        };
        let prev_trimmed = prev_content.trim();

        // Skip blank lines
        if prev_trimmed.is_empty() {
            continue;
        }

        // Skip #[...] attributes (including multi-line)
        if prev_trimmed.starts_with("#[") || prev_trimmed.starts_with("#![") {
            continue;
        }

        // Skip closing brackets of attributes (e.g., multi-line derives)
        if prev_trimmed == "]" {
            continue;
        }

        // Check if this is a TFITCX category comment
        if VALID_PREFIXES.iter().any(|prefix| prev_trimmed.starts_with(prefix)) {
            return true;
        }

        // Any other line (including non-TFITCX doc comments) means no category found
        return false;
    }
    false
}
