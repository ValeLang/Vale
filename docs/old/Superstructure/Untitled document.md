Snapshot is a templated structure, which is built in to the compiler.

On the left is Astronaut, on the right is Snapshot:Astronaut.

struct Astronaut {

(v.id): I32;

name: Str;

planet: &Planet;

}

struct Snapshot:Astronaut {

(v.id): I32;

name: Str;

planet: I32;

}

Note that \"planet\" is an \"I32\" in the snapshot.
