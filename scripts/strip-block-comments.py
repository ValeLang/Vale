#!/usr/bin/env python3
"""Strip /* ... */ block comments from Rust source on stdin, write to stdout.

Rust-aware: skips string literals (incl. raw r#"..."#), char/byte literals,
and line comments (//). Handles nested block comments per Rust spec.
"""
import sys


def strip(src: str) -> str:
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        prev = src[i - 1] if i > 0 else ''
        ident_prev = prev.isalnum() or prev == '_'

        # Line comment
        if c == '/' and nxt == '/':
            j = src.find('\n', i)
            if j == -1:
                out.append(src[i:])
                return ''.join(out)
            out.append(src[i:j])
            i = j
            continue

        # Block comment — skip, handling nesting
        if c == '/' and nxt == '*':
            depth = 1
            i += 2
            while i < n and depth > 0:
                if i + 1 < n and src[i] == '/' and src[i + 1] == '*':
                    depth += 1
                    i += 2
                elif i + 1 < n and src[i] == '*' and src[i + 1] == '/':
                    depth -= 1
                    i += 2
                else:
                    i += 1
            continue

        # Raw string: r"..." / r#"..."# / br"..." / br#"..."#
        is_raw = False
        raw_start = i
        if not ident_prev:
            if c == 'r' and (nxt == '"' or nxt == '#'):
                is_raw = True
                raw_start = i + 1
            elif c == 'b' and nxt == 'r' and i + 2 < n and src[i + 2] in '"#':
                is_raw = True
                raw_start = i + 2
        if is_raw:
            k = raw_start
            hashes = 0
            while k < n and src[k] == '#':
                hashes += 1
                k += 1
            if k < n and src[k] == '"':
                closer = '"' + ('#' * hashes)
                end = src.find(closer, k + 1)
                if end == -1:
                    out.append(src[i:])
                    return ''.join(out)
                out.append(src[i:end + len(closer)])
                i = end + len(closer)
                continue

        # Regular / byte string literal
        if c == '"' or (c == 'b' and nxt == '"' and not ident_prev):
            if c == 'b':
                out.append('b"')
                i += 2
            else:
                out.append('"')
                i += 1
            while i < n:
                d = src[i]
                if d == '\\' and i + 1 < n:
                    out.append(src[i:i + 2])
                    i += 2
                    continue
                out.append(d)
                i += 1
                if d == '"':
                    break
            continue

        # Char or lifetime
        if c == "'":
            if (nxt.isalpha() or nxt == '_'):
                k = i + 1
                while k < n and (src[k].isalnum() or src[k] == '_'):
                    k += 1
                if k >= n or src[k] != "'":
                    out.append(src[i:k])
                    i = k
                    continue
            out.append(c)
            i += 1
            if i < n and src[i] == '\\' and i + 1 < n:
                out.append(src[i])
                i += 1
                while i < n and src[i] != "'":
                    out.append(src[i])
                    i += 1
            elif i < n:
                out.append(src[i])
                i += 1
            if i < n and src[i] == "'":
                out.append("'")
                i += 1
            continue

        out.append(c)
        i += 1

    return ''.join(out)


if __name__ == '__main__':
    sys.stdout.write(strip(sys.stdin.read()))
