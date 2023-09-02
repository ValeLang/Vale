# Metadata

Every vale object has some metadata:

If managed by HGM:

 * bits 0-61: generation.
 * bit 62: Exported bit, see PerfectReplayability.
 * bit 63: 0

If managed by a special allocator:

 * bits 0-61: type info pointer ^ Region Kind ID
 * bit 62: Exported bit, see PerfectReplayability.
 * bit 63: 1

The various Region Kind IDs: see "Planned Allocators" in "Tracking Allocator Per-Object" doc.


We also might have a bit that tells us whether the object is currently owned by this thread or the outside. We could instead have a thread-local hash set for that.

When not using perfect replayability, it could be that these 64 bits are constant throughout the life of the object. If that's the case, then performance will be slightly higher as we can just do a 64 bit compare for the generations.


# Old Layout

This is before we switched HGM to mostly operate on iso objects.

If managed by HGM:

 * bits 0-31: generation.
 * bits 32-47: scope tethering bits innards, if the contained object is tetherable.
 * bits 48-61: unused, tbd!
 * bit 62: Exported bit, see PerfectReplayability.
 * bit 63: 0

If managed by a special allocator:

 * bits 0-31: type id ^ Region Kind ID
 * bits 32-47: scope tethering bits innards, if the contained object is tetherable.
 * bits 48-61: unused, tbd!
 * bit 62: Exported bit, see PerfectReplayability.
 * bit 63: 1

The various Region Kind IDs: see "Planned Allocators" in "Tracking Allocator Per-Object" doc.
