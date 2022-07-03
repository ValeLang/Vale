fn attack(virtual u1: Unit, virtual u2: Unit) {

}

fn attack(override p: SuperPlayer, override g: Goblin) {

}

fn attack(override p: SubPlayer, override k: Kobold) {

}

SuperPlayer extends Unit

SubPlayer extends SuperPlayer

Goblin extends Unit

Kobold extends Unit

calling attack(a, b) should know that its about to look up a
multimethod.

it will look in the edge table for Unit for the function
\_\_dispatch:0:attack:2:Unit:Unit.

\_\_dispatch:N the N says which parameter we\'re dispatching on.

example:

\_\_dispatch:0:attack:2:SuperPlayer:Unit will be called with a
SuperPlayer. It then calls \_\_dispatch:1:attack:2:SuperPlayer:Unit on
the second arg.

everyone who implements Unit should have an entry for:

\_\_dispatch:0:attack:2:Unit:Unit

\_\_dispatch:1:attack:2:SuperPlayer:Unit

\_\_dispatch:1:attack:2:SubPlayer:Unit

SuperPlayer:

\_\_dispatch:0:attack:2:Unit:Unit =
\_\_dispatch:0:attack:2:SuperPlayer:Unit

\_\_dispatch:1:attack:2:SuperPlayer:Unit = attack:2:Unit:Unit

\_\_dispatch:1:attack:2:SubPlayer:Unit = attack:2:Unit:Unit

SubPlayer:

\_\_dispatch:0:attack:2:Unit:Unit =
\_\_dispatch:0:attack:2:SubPlayer:Unit

\_\_dispatch:1:attack:2:SuperPlayer:Unit = attack:2:Unit:Unit

\_\_dispatch:1:attack:2:SubPlayer:Unit = attack:2:Unit:Unit

Goblin:

\_\_dispatch:0:attack:2:Unit:Unit = attack:2:Unit:Unit

\_\_dispatch:1:attack:2:SuperPlayer:Unit = attack:2:SuperPlayer:Goblin

\_\_dispatch:1:attack:2:SubPlayer:Unit = attack:2:Unit:Unit

Kobold:

\_\_dispatch:0:attack:2:Unit:Unit = attack:2:Unit:Unit

\_\_dispatch:1:attack:2:SuperPlayer:Unit = attack:2:Unit:Unit

\_\_dispatch:1:attack:2:SubPlayer:Unit = attack:2:SubPlayer:Kobold
