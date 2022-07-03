refcell - guarantee 1 writer, or \>=1 readers.

problem in rust is that you have to use Rc with it.

what i want is an owning thing of that, and then we can borrow it, but
then we can lock it to get a & or an &!.

shouldnt we make this default though? is it even possible?

maybe we dont really need a borrow_mut. we just need to count the active
reads?

so we need a way to lock it down.

we can have a \"lockable borrow ref\" and a \"lock borrow ref\". when
you\...

no, this is silly. we should just have a way to (deeply?) lock down an
object. some sort of special locking borrow? instead of &x, have &lock
x? itll set the high bit of the borrow counter.

and its deterministic, so, easy. there, solved the whole problem.

maybe a better word than &lock. &lck perhaps

if someone else owned an object, how would we call into them to ask them
to delete it?

let myBorrowRef = \...;

manager.deletePlease(myBorrowRef);

i\'ll undoubtedly get an error at runtime because myBorrowRef is still
active, but\... i didnt use it anymore\...

option A:

detect that we never use it again, and immediately destroy it as we\'re
handing it to the receiving function.

option B:

require that we normally do borrow-to-borrow passing with
doSomething(&myBorrowRef). if we just say doSomething(myBorrowRef) then
we\'re moving the reference.

in list\'s remove, can we annotate the incoming borrow ref as \@Doomed?

then that would make sure we have no other references to it. could be a
useful analysis\...
