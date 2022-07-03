// Creates a new struct instance.

case class NewStructH(

// Registers containing the values we\'ll use as members of the new
struct.

// As with any read from a register, this will invalidate the register.

sourceRegisters: List\[ExpressionH\[ReferendH\]\],

// The type of struct we\'ll create.

resultType: ReferenceH\[StructRefH\]

)

// Takes a reference from the given \"struct\" register, and copies it
into a new

// register. This can never move a reference, only alias it.

case class MemberLoadH(

// Register containing a reference to the struct whose member we will
read.

// As with any read from a register, this will invalidate the register.

structRegister: ExpressionH\[StructRefH\],

// Which member to read from, starting at 0.

memberIndex: Int,

// The ownership to load as. For example, we might load a constraint
reference from a

// owning Car reference member.

targetOwnership: OwnershipH,

// The type we expect the member to be. This can easily be looked up,
but is provided

// here to be convenient for LLVM.

expectedMemberType: ReferenceH\[ReferendH\],

// The type of the resulting reference.

resultType: ReferenceH\[ReferendH\],

// Member\'s name, for debug purposes.

memberName: FullNameH

)

-   in compileValeCode, for each struct,

    -   declare it (kinda like declareFunction)

    -   fill its members in (kinda like translateFunction)

-   NewStruct

    -   Allocate local variable (like MyStruct m;)

    -   Set all variables, like:

        -   m.x = 6;

        -   m.y = 8;

        -   m.z = 10;

-   MemberLoad

    -   Get the pointer to the member

        -   Result will be an i32\*, or perhaps a struct\*\*

    -   Load that pointer

        -   Result will be an i32, or perhaps a struct\*

struct MyStruct { a Int; }

struct OtherStruct { b MyStruct; }

fn main() {

print(

if (readInput() == 17) {\
= readInput() \* 5;

} else {

blarg = readInput() \* 7;\
= blarg;

});

hps = myMarines.map({ \_.hp });

ms = OtherStruct(MyStruct(11));

= ms.b.a;

}

[[https://pastebin.com/jugJWWCh]{.underline}](https://pastebin.com/jugJWWCh)

int y;

if (readInput() == 17) {\
y = readInput() \* 5;

} else {\
y = readInput() \* 7;

};
