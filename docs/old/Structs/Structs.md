## Structs

struct namehere:(template params here) {

methodname:(template params here)(this: &!, a: int) { ... }

}

interface IStrengthAffector {

fn affectStrength(strength: Int): Int;

}

you can make an anonymous one like this:

let affector = IStrengthAffector{\[\](str: Int) str + 3}

its only argument is the lambda, which is why we can do braces like
that, its a unary function thing

or perhaps we wanna do it java style?

let a: IStrengthAffector = {\[\](str: Int) str + 3}

automatically makes a

let a: IStrengthAffector = StrengthAffector{

override fn calculateStrength(this, strength: Int): Int = str + 3;

};
