fn if {(condition: ()bool)(thenBody: ()#T1)Option:#T1

switch(condition())(

{(true) thenBody()},

{(false) None:T1()})

}

fn else {(thing: Option:#T1, elseBody: ()#T2)(#T1\|#T2)

switch(thing)(

{(:Some:T1(inner)) inner},

{(:None:T1) elseBody()})

}

fn elseif {(thing: Option:#T1, condition: ()bool)(thenBody:
()#T2)Option:#T2

switch(thing)(

{(:Some:T1(inner)) inner},

{(:None:T1) if condition thenBody})

}
