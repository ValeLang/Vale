// MySpaceship is the "entity"

// Though really, any struct can provide things for its members to use;
in other words, any struct can be thought of as an entity if it has
components and provides things for them

struct MySpaceship {

implements ISpaceship;

**provide** fuelSource = MyFuelSource();

// these two below dont actually need to provide, since theyre not
required by any components

weapons = MyWeapons**(this)**;

engines = MyEngines**(this)**;

// \^ when we instantiate a component, we need to supply the containing
entity as the first

// argument, so the component can extract its requirements from the
entity

}

// MyFuelSource/IFuelSource have no requirements, so they're vanilla
struct/interface

interface IFuelSource {

fn decreaseFuel(this, amount: f32);

}

struct MyFuelSource {

implements IFuelSource;

fuel: f32;

fn decreaseFuel(this, amount: f32) { this.fuel = this.fuel - amount; }

}

// MyEngines requires the containing entity to provide a IFuelSource

struct MyEngines {

implements IEngines;

**requires** fuelSource: IFuelSource; // Anyone can require any
interface

**requires** inertialDampener: IInertialDampener;

**requires** neutroniumFlamscranklifier: INeutroniumFlamscranklifier;

thrust: f32;

fn go() {

fuelSource.decreaseFuel(thrust);

}

}

struct MyEngines:(

E;

E \<: IHasA:IFuelSource;

E \<: IHasA:IInertialDampener;

E \<: IHasA:INeutroniumFlamscranklifier

) {

implements IEngines;

fuelSource: IFuelSource;

inertialDampener: IInertialDampener;

neutroniumFlamscranklifier: INeutroniumFlamscranklifier;

thrust: f32;

fn MyEngines(entity: E, thrust: f32) {

this.thrust = thrust;

this.fuelSource = entity.get:IFuelSource();

this.inertialDampener = entity.get:IInertialDampener();

this.neutroniumFlamscranklifier =
entity.get:INeutroniumFlamscranklifier();

}

fn go() {

fuelSource.decreaseFuel(thrust);

}

}

struct MyEngines:E {

implements IEngines;

fn getFuelSource(): IFuelSource {

return this - offsetOf:(E, MyEngines)() + offsetOf:(E, IFuelSource)();

}

fn getInertialDampener(): IInertialDampener {

return this - offsetOf:(E, MyEngines)() + offsetOf:(E,
IInertialDampener)();

}

fn getNeutroniumFlamscranklifier(): INeutroniumFlamscranklifier {

return this - offsetOf:(E, MyEngines)() + offsetOf:(E,
INeutroniumFlamscranklifier)();

}

thrust: f32;

fn MyEngines(thrust: f32) {

this.thrust = thrust;

}

fn go() {

getFuelSource().decreaseFuel(thrust);

}

}

// MyWeapons requires the containing entity to provide a IFuelSource

struct MyWeapons {

implements IWeapon;

**requires** fuelSource: IFuelSource;

damage: f32;

fn fire(enemy: Enemy) {

enemy.hp -= damage;

fuelSource.decreaseFuel(damage);

}

}

other capabilities not shown here:

-   an entity can provide one of its members upward, in other words, an
    > entity can provide multiple things, for use in another
    > "grandfather" entity

-   components can be nested; component B can contain component C.
    > component C has a requirement that component B cannot provide, so
    > component B can itself require it. component A can later contain
    > component B and provide the requirements.

-   automatic updating; if i swap out MyWeapons for another MyWeapons,
    > it's automatically updated everywhere else

-   automatic observing via "observe" keyword
