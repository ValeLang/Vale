# Metadata

Every vale object has some metadata:

If managed by HGM:

 * bits 0-31: generation.
 * bits 32-47: scope tethering bits innards, if the contained object is tetherable.
 * bits 48-61: unused, tbd!
 * bit 62: Exported bit, see DeterministicReplayability.
 * bit 63: 0

If managed by a special allocator:

 * bits 0-31: type id ^ Region Kind ID
 * bits 32-47: scope tethering bits innards, if the contained object is tetherable.
 * bits 48-61: unused, tbd!
 * bit 62: Exported bit, see DeterministicReplayability.
 * bit 63: 1

The various Region Kind IDs: see "Planned Allocators" in "Tracking Allocator Per-Object" doc.


Note: Can't multiply by 8 the top 8 bits of a pointer for the offset-to-generation, because this pointer might be pointing at a 1-byte character in the middle of an object. It won't always be a multiple of 8. So, we'll need a generation every 256 bytes for large objects if we want to go that route. Might be a good idea to save space still.