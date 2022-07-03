interface IUnit {

hp: Int;

target: IUnit;

fn attack();

}

struct Firebat implements IUnit {

hp: Int;

fuel: Int;

target: IUnit;

fn override attack() {

fuel -= 10;

target.hp -= 10;

}

}

struct Marine implements IUnit {

hp: Int;

bullets: Int;

target: IUnit;

fn override attack() {

bullets -= 3;

target.hp -= 8;

}

}

struct Player {

units: Set\<IUnit\>;

}

struct Game {

players: Array\<Player\>;

fn doTurn(game: &Game) {

game.players.,units..attack();

}

}

.. is the map operator

., is the flatmap operator

// assuming other things can own IUnits

// assuming only Game can own Player

create table Marine (

id int,

hp int,

bullets int,

target int (not a foreign key)

)

create table Firebat (

id int,

hp int,

bullets int,

target: int (not a foreign key)

)

create table Player (

id int

)

create table PlayerUnitsMarines (

playerId int, (foreign key Player)

marineId int (foreign key Marine)

)

create table PlayerFirebatMarines (

playerId int, (foreign key Player)

firebatId int (foreign key Firebat)

)

create table Player (

id int,

gameId int

)

create table Game ()

stored procedure doTurn(gameId: game) {

create temporary table r1 = (select playerId from Player where gameId =
game);

create temporary table r2a =

(select \* from r1 cross join PlayerUnitsMarines pum where r1.playerId =
pum.playerId);

create temporary table r2b =

(select \* from r1 cross join PlayerUnitsFirebats puf where r1.playerId
= puf.playerId);

// "r2" would be the result of that game.players.,map. it doesnt exist,
but r2a+r2b will serve.

// now we need to map the attack() abstract function to r2. not hard,
since its all concrete

// types anyway.

// "r3" is the result of attack.

create temporary table r3a =

man this is really hard...

}

[[https://stackoverflow.com/questions/13474012/invoke-pusher-when-mysql-has-changed]{.underline}](https://stackoverflow.com/questions/13474012/invoke-pusher-when-mysql-has-changed)
can notify when things have changed! (see suggestion about writing to a
different table)

i dont know if this will tell us about intermediate effects. like if in
a transaction we change jimmy's hp to 6 then 5, will it just tell us the
5? pretty sure firebase has this same optimization. perhaps we should
just universally decree that effects are only diffing.
