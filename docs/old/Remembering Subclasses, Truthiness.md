[[Ifs]{.underline}](#ifs)

> [[How it Works]{.underline}](#how-it-works)

[[Matches]{.underline}](#matches)

# Ifs

(assuming unit: ?IUnit)

Short form:

> if weapon {
>
> \...
>
> println(unit.hp);
>
> }

If either of these are true:

-   weapon is a final variable, which means itll never change

-   we can conclude it can\'t change between the if weapon and weapon\'s
    > usage.

then that will implicitly make a variable of the same name, borrowing
the old thing inside the optional.

If not, then they\'ll have to use the longer form:

> if let \[weapon\] = &weapon {
>
> \...
>
> println(unit.hp);
>
> }

## How it Works

When if receives a non-boolean, it will implicitly convert to the longer
form. It\'ll follow normal interface-matching rules then.

# Matches

We can do the same thing for match statements:

> mat unit {
>
> if :Firebat {
>
> println(unit.fuel);
>
> }
>
> if :Wraith {
>
> println(unit.altitude);
>
> }
>
> }

It will recognize that we just matched on a simple variable. It will
check if either of these is true:

-   unit is a final variable, which means itll never change

-   we can conclude it can\'t change between the mat unit and unit\'s
    > usage.

then we can use unit as if it was the concrete type.
