
# Frequently Asked Questions

### Why does `/query-actor-history` not show anything for someone I banned?

The `/query-actor-history` command lists events that were *initiated or performed by* a user, e.g., a group staff user.
You should be using the `/query-target-history` command instead.

### How do I remove an audit channel?

If you want to stop logging a certain event to a channel, simply *omit* the channel parameter of the appropriate command, like this: `/set-audit-channel audit-event-type:'Instance Close'`

