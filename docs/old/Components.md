~~mixins can have virtual fields and virtual methods. they'll all be
namespaced differently.~~

~~mixins can have requirements, and require that certain interfaces be
present.~~

~~the struct can implicitly access any fields of its parent traits,
unless there are multiple of the same name, then we have to use
something like this.as:MyFirstTrait().theField.~~

~~maybe a struct can't have two mixins that implement the same
interface.~~

~~perhaps we can provide a default for a mixin's requirement
interfaces?~~

~~mixin MySpaceship requires IEngines = WarpEnginesMixin {~~

~~}~~

~~what im really trying to do here is ECS where we dont need to say
GetComponent() all over the place. i want to give components access to
each other (which is why they can have requirement interfaces), separate
the components implementations from each other (which is why they can
only require interfaces, not other mixins). i want to be able to treat
MyEntity as an IEngines~~

component MyEngines implements IEngines requires IFuelSource {

}

components can implement interfaces, and require interfaces.

a struct can contain a component, thus giving other components access to
it. theyll all hook up to each other. the parent struct can do
forwarding, if it wants to do the inheritance sort of thing. MyCarShip
can bring in the Car component, and also forward an ICar to it.

containing something as a regular struct gives it no access to any of my
things.

containing something as a component means we promise to provide it with
its requirements.

then again, there's really no difference, when a component has no
requirements.

perhaps we can just give structs the ability to require things of their
parent structs? then that makes them component-ish.

struct MyEngines implements IEngines requires IFuelSource

that means if you contain a MyEngines, you \*have\* to provide an
IFuelSource. It also means this is basically abstract; you cant allocate
it on its own because it requires an IFuelSource. it needs to be inside
another structure which has one.

this works super well!

also, there's not really namespacing problem since this is pure ECS.

struct WarpEngines implements IEngines {

fuelSource: requires IFuelSource;

}

struct Wings implements ILift {

wingControl: requires IWingControl;

}

struct Navi implements INavigator {

sensors: requires ISensor;

}

interface ISensorsObserver {

void onShipDetected();

}

interface ISensor observable ISensorsObserver { }

struct StarSensors implements ISensor { }

field: requires InterfaceType;

requires means we require that interface.

field: component StructType;

component means thats a member of me, i own it.

it introduces its interfaces into the struct's component pool, and hook
up requirements to it.

struct Firefly : public ISpaceship: private ISensorsObserver {

engines: component WarpEngines;

wings: component Wings;

navi: component Navigator;

sensors: observed component StarSensors;

}

if two components provide the same interface, then that's a compiler
error.

all components will be allocated and have their pointers hooked up when
the parent is allocated. then, the parent constructor will be called
first, and then the rest of them in order of requirements. something
will be constructed after its requirements; it's topo sorted like that.

~~so, question... how does a component communicate upward to its parent?
we probably have to allow for that somehow. perhaps that's where the
CRTP can come in? hmmm... perhaps we should have a method for a
component to get its parent? perhaps via a special interface?~~

~~maybe we can make it so if the parent implements an interface, it's
introduced into its own component pool, so the children can get hooked
up to it. would that be too tempting? perhaps we should add a special
IWaking:T interface, which is automatically called with a pointer to the
parent T? nah, the person can do that on their own. thats probably the
best idea.~~

"observable XYZ" adds a vector of XYZ references. it also means we can
just call those methods directly, like this.onEnemyDetected() and that
will automatically loop through all observers.

we should throw a warning if we detect someone trying to fire an event
in a constructor, because it wont do anything.

observed means we add ourselves as an observer, after all the
constructors have been called. we add ourselves as an observer on our
observed components in the same topo order.

yes, it all links up perfectly. very nice.

want the ability to provide components transitively upwards, could be
useful in large scale architectures?

struct Synthesizer {

requires audioOutput: IAudioOutput; // says that we or the parent needs
to provide it

void playSound() {

}

}

struct MyAudioManager {

// implements implies provides, so this means we also provide an
IAudioManager

implements IAudioManager;

// provides means that we introduce it into our parent scope

provides ISynthesizer;

// requires means that we require it to be in our scope somehow

requires IAudioOutput;

// component introduces its provides into our scope, and gives our
provides into its scope.

synthesizer: component Synthesizer;

}

struct MyGame {

}

struct MySpaceship {

implements ISpaceship;

provide fuelSource = MyFuelSource();

provide engines = MyEngines(this);

}

struct MySpaceship {

implements ISpaceship;

implements HasA:IFuelSource;

implements HasA:IEngines;

engines = MyEngines(this);

}

interface IFuelSource {

fn decreaseFuel(this, amount: f32);

fn hasFuel(this, amount: f32) : bool;

}

struct MyFuelSource {

implements IFuelSource;

fuel: f32;

fn decreaseFuel(this, amount: f32) { this.fuel = this.fuel - amount; }

fn hasFuel(this, amount: f32) : bool = this.fuel \>= amount;

}

(no change)

struct MyEngines {

implements IEngines;

requires fuelSource: IFuelSource;

thrust: f32;

}

struct MyEngines(E; E \<: HasA:IFuelSource) {

implements IEngines;

fuelSource: IFuelSource;

thrust: f32;

fn MyEngines(this, entity: E, thrust: f32) {

this.fuelSource = entity.get:IFuelSource();

this.thrust = thrust;

}

}

struct MyWeapons {

implements IWeapon;

requires fuelSource: IFuelSource;

damage: f32;

}

interface hasa:T can be used for components! it can have a virtual int
offset. nah, need to optimize that.

putting a provides in should make us implement HasA:T, which has a
virtual get\<T\>() for that thing.

we \*could\* implement components in general with this, to start. later
on we can do optimizations with the etable\<thing, offset\>.

how do we do experiments to switch back and forth between two
subcomponents?

i suppose we\'d have a

provides myThing: IWhatever;

and then in the constructor it would be one thing or another.

struct DocumentManager {

umsRequester: UmsRequester;

documents: List:Document;

}

List:Document doesn\'t need component, because it doesn\'t need to be
automatically hooked up. when we want to add a document, we\'ll say
documents.append(Document(this, 10));

That \'this\' argument does all the connecting, and is our permission to
grab our components.

general pattern: it\'s fine and even good to have roadblocks to exposing
our data upwards.

can the outside see it?

can my subclasses see it?

can my components see it?

-   (none)

    -   private

    -   yes. definitely.

-   components

    -   (not subclasses, not outside)

    -   yeah, makes sense. components are a private implementation
        > detail. we might want to give them access so that they can
        > manipulate something of ours.

-   subclasses

    -   \"protected\"

    -   kind of? i suppose we dont want some random component from
        > narnia to use our private data.

    -   we might want subclasses to operate on our components, without
        > the components operating on each other?

-   subclasses components

    -   this is a hard one.

-   outside

    -   not much sense. this doesnt even exist in java.

-   outside components

    -   (so, not subclasses).

    -   not much sense. this doesnt even exist in java.

-   outside subclasses

    -   (so, not components).

    -   public

    -   makes little sense. outside can modify things at will, but
        > components cant?

-   outside, subclasses, components

    -   public

    -   makes sense.

public: outside, subclasses

something: outside, subclasses, components

something: subclasses, components

subclasses: protected

components: private-ish

(none): private

private, (components), protected, (subclasses components), public
(outside, subclasses, components)

require can be transitive

provide only has to happen once, somewhere in an ancestor

since require is transitive, that means it only has to happen deep down,
where we actually need it. if i require something, \*i\* need it, not my
children. if my children needed it, \*they\* would require it. so,
require doesnt introduce something into the pool.

if i want to introduce something into the pool myself\... \"share\"?
\"provide\"?

\# in an expression context means component, hand in a bunch of things
as the first argument

\# in a type context means we\'re giving it access to our pool and
we\'re absorbing its pool?

it also means that that thing better be connected to us. if its not, its
a bug.

\...huh... if we\'re pointing at something else which has a Destructible
component, are we pointing at the entity? or the component? perhaps
both?

i think every component should have a pointer to the parent. the
parent\'s first field is a pointer to an ETable\<ComponentInterfaceID,
Offset\>. so, to get a different component, we would do two lookups: 0,
then the target.

theAdapter is \"component ?TheAdapter\" instead of \"? component
TheAdapter\" because they\'re made on demand, like Document.

\"component\" usually does two things: grant access to our pool, and put
them (and their provides) in our pool.

\"component\" on \"theAdapter: ?TheAdapter\" only really puts that
Option:TheAdapter into our pool. it doesnt provide any of their stuff:

-   \...which kind of makes sense, because an option cant provide
    > anything

-   \...which kind of makes sense, because it might not exist yet.

-   \...which kind of makes sense, because when we eventually create it,
    > it will be using a line like \"this.theAdapter = TheAdapter(this,
    > otherstuff, otherstuff)\" and the \'this\' is where we give it
    > access to our pool.

Document\'s real constructor would take in its requirements and explicit
args:

fn Document(requester: &TheRequester)(key: Int)

so, when we want to make a Document, we would say
this.componentMaker\<Document\>()(10);

componentMaker automatically looks through a struct\'s requirements, and
gives a lambda that creates and connects.

or, better form: Document(this, 10);

struct TheAdapter implements ITheAdapter {

requires theRequester: TheRequester;

exportOperation: component ExportOperation;

importOperation: component ImportOperation;

}

a component can\'t be instantiated solo, so we can\'t really own a
component without providing access to our pool. so we dont really need
to say \"component\" beforehand... perhaps we should enforce that
components begin with C or something?

required things are added as constructor arguments. theres also another
constructor, which takes in a templated first argument, which is the
root component pool, which can be used to extract the right arguments.

struct MyAudioManager {

// implements implies provides, so this means we also provide an
IAudioManager

implements IAudioManager;

// provides means that we introduce it into our parent scope

provides ISynthesizer;

// requires means that we require it to be in our scope somehow

requires IAudioOutput;

// component introduces its provides into our scope, and gives our
provides into its scope.

synthesizer: component Synthesizer;

}

struct MyGame {

}

### Events and Requests

struct MyEngines {

mainThruster = WarpThruster(this);

reserveThrusterA = ImpulseThruster(this);

reserveThrusterB = ImpulseThruster(this);

event fn ranOutOfFuel(:RanOutOfFuelEvent);

request fn pickAReserveThruster(:PickAReserveThruster) : bool;

// \_ says it can come from any of our subcomponents. also means it can
be hooked up

// at runtime, when we instantiate a new component manually

observe(\_) fn(e:RanOutOfFuelEvent) {

if {reserveThrusterA.isEmpty and reserveThrusterB.isEmpty} {

this.ranOutOfFuel(e);

}

observe(mainThruster) fn(:RanOutOfWarpiumEvent) {

print("i ran out of fuel!");

}

}

struct RanOutOfFuelEvent { }

struct ImpulseThruster {

event ranOutOfFuel RanOutOfFuelEvent;

}

if we really find a use case for forwarding an event upwards, we can
consider adding syntax for it. not yet though.

right now, we enforce that event functions and request functions take in
a struct. this is so we have something in the signature that's
namespaced; function names are not really namespaced that well. for
example, in java, i can override doThing() but i dont really specify
whose doThing() im overriding.

theoretically we could add parameters, but it would make things a bit
more confusing. lets just stick to one struct.

event and request functions can only take in immutable objects.

event functions can have any number of handlers, 0, 1, 2, N\...

request functions can only have one handler.

observe
