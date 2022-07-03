Let\'s say we had this structure:

> struct Loc imm { groupX: Int, groupY: Int, indexInGroup: Int; }
>
> struct LevelTemplate imm {
>
> name: Str;
>
> entries: CMap\<Loc, CList\<Str\>\>;
>
> }

In proto it would be an uneditable byte string.

In simple xml it would be:

> \<LevelTemplate name=\"starting\"\>
>
> \<entries\>
>
> \<MapEntry\>
>
> \<key\>\<Loc groupX=\"1\" groupY=\"-2\" indexInGroup=\"4\" /\>\</key\>
>
> \<value\>
>
> \<List\>
>
> \<ListEntry\>\"grass\"\</ListEntry\>
>
> \<ListEntry\>\"tree\"\</ListEntry\>
>
> \</List\>
>
> \</value\>
>
> \</MapEntry\>
>
> \...
>
> \</entries\>
>
> \</LevelTemplate\>

In JSON it would be:

> {
>
> name: \"starting\",
>
> entries: \[
>
> {key: {x: 1, y: -2, z: 4}, value: \[\"grass\", \"tree\"\]},
>
> {key: {x: 1, y: -2, z: 3}, value: \[\"grass\", \"rock\"\]},
>
> {key: {x: 1, y: -2, z: 1}, value: \[\"grass\"\]},
>
> {key: {x: 1, y: -2, z: 5}, value: \[\"grass\", \"player\"\]},
>
> \],
>
> \]

In VON it would be:

> (
>
> \"starting\",
>
> (
>
> ((1, -2, 4), (\"grass\", \"tree\")),
>
> ((1, -2, 3), (\"grass\", \"rock\")),
>
> ((1, -2, 1), (\"grass\")),
>
> ((1, -2, 5), (\"grass\", \"player\")),
>
> ),
>
> )

VON aims to be as human editable like JSON/XML, as compact as JSON, as
type-safe as XML, and have an easy escape hatch to proto if they need
forward compatibility.

# Forwards/Backwards Compatibility

## Removing a Field

We would make the sender stop specifying that field.

We would have the receiver add a constructor that doesn\'t take that
particular argument, or we can change it to have a default value for
that particular argument.

If a new sender sends data to an old receiver, it\'s going to crash.
But, it would have crashed anyway even if we did have optional
fields\...

If an old sender sends data to a new receiver, itll work just fine.

## Adding a Field

We would make the sender fill that in.

We would have the receiver add a constructor that takes the new
argument, or we can change it to have a default value for that
particular argument.

If a new sender sends data to an old receiver, it\'ll work just fine,
receivers by default ignore fields that they don\'t know about.

If an old sender sends data to a new receiver, it\'ll use the old
constructor or the default value and it\'ll work just fine.

The only problematic case is when we\'re removing a field that the
client relies upon. But that\'s better caught as early as possible\...

Let\'s make it so there\'s a fromProto() method that we can easily
override, so they can handle this complexity in their own way if they
want to.

In fact, we should make this entire thing some sort of opt-in compiler
plugin or macro, like rust\'s derive.

fn fromProto(bytes: \'&Array\<Byte\>) Marine =
