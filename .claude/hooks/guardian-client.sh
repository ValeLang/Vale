#!/bin/bash
# Sends hook input to the guardian server and outputs the response.
# Usage: guardian-client.sh PORT

if [ -z "$1" ]; then
    echo "guardian-client.sh: missing required PORT argument" >&2
    exit 2
fi
PORT="$1"

RESPONSE=$(curl -s -X POST "http://127.0.0.1:${PORT}/validate" \
    -H "Content-Type: application/json" \
    -d @- 2>&1)

if [ $? -ne 0 ]; then
    # Server not reachable - allow the edit (don't block if server is down)
    echo '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"guardian server not reachable on port '"${PORT}"'"}}'
    exit 2
fi

echo "$RESPONSE"

# Check if the response contains a deny decision
if echo "$RESPONSE" | grep -q '"permissionDecision":"deny"'; then
    exit 2
fi

exit 0
