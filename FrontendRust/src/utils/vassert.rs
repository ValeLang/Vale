
// The other v-helpers above (vcheck/vassert/vcurious/vassertSome) and below (vfail/vwat/vimpl/
// vregion/vregionmut) are realized inline in Rust as `assert!`/`panic!`/`.expect()` at their call
// sites (per migration-policy), so they have no Rust fn here. `vassertOne` is the exception: it
// returns the sole element, which has no built-in idiom, so it's a real helper.
pub fn vassert_one<T>(thing: impl IntoIterator<Item = T>) -> T {
    let mut iter = thing.into_iter();
    match iter.next() {
        None => panic!("Expected one element, but was empty."),
        Some(x) => {
            let extra = iter.count();
            if extra == 0 {
                x
            } else {
                panic!("Expected one element, but was size {}.", extra + 1)
            }
        }
    }
}

