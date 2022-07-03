Mutabase - The easy, real-time, collaborative database.

(on right: video of me recreating google docs in under an hour)

(on right: video of me making subterfuge in under an hour)

A Mutabase is a database that can:

-   Rewind to any point in time.

-   Automatically replicate its data to other threads or machines.

-   Merge edits from other machines.

-   Enforce arbitrary code constraints via functions.

Define a simple schema:

root struct School {

teachers: Set\<Teacher\>;

students: Set\<Student\>;

classes: Set\<Class\>;

}

struct Teacher {

name: String;

}

struct Student {

name: String;

classes: List\<&Class\>;

}

struct Class {

code: Int32;

teacher: &Teacher;

students: Set\<&Student\>;

}

and some requests:

request fn enrollInAllClasses(school: &School, student: &Student) {

each school.classes as class {

student.classes.insert(class);

class.students.insert(student);

}

}

and it automatically generates classes you can use in your C++, Swift,
Java, C#, or Javascript:

let school = mutabase.getRoot();

let student = school.getStudentByName("Zeke");

let class = school.getClassByCode(101);

console.log("Enrolling student ", student.getName(), " in all
classes!");

mutabase.enroll(school, student).then(() =\> console.log("Success!"));

Why use a mutabase over a SQL database?

-   
