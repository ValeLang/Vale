superstructure OrccaModel { // online realtime collaborative coding app

root struct Document {

text!: Str;

}

fn replace(document: &!Document, begin: Int, end: Int, text: Str) {

mut document.text! = document.text!.splice(begin, end - begin, text);

}

}

make it so we can observe it and have our caret move around
appropriately

// Server

let orcca_owning = OrccaModel(Document(\"\"));

let server = SimpleSuperstructureServer(orcca_owning, 8080);

let orcca = server.superstructure;

// Client

let client = SimpleSuperstructureClient(\"localhost\", 8080);

let caretBegin! = 0;

let caretEnd! = 0;

let orcca = server.superstructure;

foreach (and(orcca.effects, cin)) {

{:Effect:replace(doc, begin, end, text)

if (begin \< caretBegin!) and (caretBegin! \< end) {

mut caretBegin! = begin;

} else if (caretBegin! \> end) {

mut caretBegin! = caretBegin! - (end - begin);

}

if (begin \< caretEnd!) and (caretEnd! \< end) {

mut caretEnd! = begin;

} else if (caretEnd! \> end) {

mut caretEnd! = caretEnd! - (end - begin);

}

}

{(text: Str)

document.replace(caretBegin!, caretEnd!, text);

}

});

gotta connect to something like polymer or react for the biggest wow
factor, dont use qt. java and swift will inevitably lead to questions of
when the other will be ready, so do js. ugh js is such a pain though.

all that client code can be in javascript. next iteration will move it
into the superstructure.
