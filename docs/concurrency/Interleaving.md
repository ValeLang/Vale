

```
results =
interleaved(4) foreach ship in myShipsList {
	totalFuel = ship.leftEngine.fuel + ship.rightEngine.fuel;
	if totalFuel < 10 {
		set totalFuel = totalFuel * 2;
	} else {
		sequential { println(totalFuel); }
	}
	totalFuel
};
```

interleaved(4) implies unroll(4) and pure.

would become:


```
len = myShipsList.len();
results = List<i64>(len);
for (int i = 0; i < len; i += 4) {
	iA = i;
	iB = i + 1 < len ? i + 1 : i;
	iC = i + 2 < len ? i + 2 : i;
	iD = i + 3 < len ? i + 3 : i;
	shipA = myShipsList.get(iA); // inlined
	shipB = myShipsList.get(iB); // inlined
	shipC = myShipsList.get(iC); // inlined
	shipD = myShipsList.get(iD); // inlined
	sleA = shipA.leftEngine; sleB = shipB.leftEngine; sleC = shipC.leftEngine; sleD = shipD.leftEngine;
	slefA = sleA.fuel; slefB = sleB.fuel; slefC = sleC.fuel; slefD = sleD.fuel;
	sreA = shipA.rightEngine; sreB = shipB.rightEngine; sreC = shipC.rightEngine; sreD = shipD.rightEngine;
	srefA = sreA.fuel; srefB = sreB.fuel; srefC = sreC.fuel; srefD = sreD.fuel;
	totalFuelA = slefA + srefA; totalFuelB = slefB + srefB; totalFuelC = slefC + srefC; totalFuelD = slefD + srefD;
	condA = totalFuelA < 10; condB = totalFuelB < 10; condC = totalFuelC < 10; condD = totalFuelD < 10;

	// Find a true
	trueTotalFuelDefault = condB ? &totalFuelB : &totalFuelA;
	trueTotalFuelDefault = condC ? &totalFuelC : trueTotalFuelDefault;
	trueTotalFuelDefault = condD ? &totalFuelD : trueTotalFuelDefault;

	trueTotalFuelPtrA = condA ? &totalFuelA : trueTotalFuelDefault;
	trueTotalFuelPtrB = condB ? &totalFuelB : trueTotalFuelDefault;
	trueTotalFuelPtrC = condC ? &totalFuelC : trueTotalFuelDefault;
	trueTotalFuelPtrD = condD ? &totalFuelD : trueTotalFuelDefault;

	if condA or condB or condC or condD {
		// Load
		trueTotalFuelA = *trueTotalFuelPtrA;
		trueTotalFuelB = *trueTotalFuelPtrB;
		trueTotalFuelC = *trueTotalFuelPtrC;
		trueTotalFuelD = *trueTotalFuelPtrD;

		// Do actual instructions
		trueTotalFuelA = trueTotalFuelA * 2;
		trueTotalFuelB = trueTotalFuelB * 2;
		trueTotalFuelC = trueTotalFuelC * 2;
		trueTotalFuelD = trueTotalFuelD * 2;

		// Send back into parent scope
		*trueTotalFuelPtrA = trueTotalFuelA;
		*trueTotalFuelPtrB = trueTotalFuelB;
		*trueTotalFuelPtrC = trueTotalFuelC;
		*trueTotalFuelPtrD = trueTotalFuelD;
	}

	// Collect into an array
	falseNextI = 0;
	falseTotalFuels = [#8]i64(null);
	falseTotalFuels[falseNextI] = &totalFuelA; falseNextI += !condA;
	falseTotalFuels[falseNextI] = &totalFuelB; falseNextI += !condB;
	falseTotalFuels[falseNextI] = &totalFuelC; falseNextI += !condC;
	falseTotalFuels[falseNextI] = &totalFuelD; falseNextI += !condD;
	for (int x = 0; x < falseNextI; x++) {
		println(falseTotalFuels[x]);
	}




	trueTotalFuels[falseNext] = &totalFuelA; falseNext -= condA;
trueTotalFuels[falseNext] = &totalFuelB; falseNext -= condB;
trueTotalFuels[falseNext] = &totalFuelC; falseNext -= condC;
trueTotalFuels[falseNext] = &totalFuelD; falseNext -= condD;




	set totalFuel = totalFuel * 2;


	switch (trueNext) {
	case 0: // None true, all false
		println(trueTotalFuels[0]);
		println(trueTotalFuels[1]);
		println(trueTotalFuels[2]);
		println(trueTotalFuels[3]);
		break;
	case 1: // 1 true, 3 false
		set totalFuel = totalFuel * 2;
		trueTotalFuels

	case 2: // 2 true, 2 false

	case 3: // 3 true, 1 false
	}

	a = get(i * 4 < len ? i * 4 
}

foreach slice in myShipsList
unroll(8) pure interleaved foreach ship in myShipsList {
	averageFuel = (ship.leftEngine.fuel + ship.rightEngine.fuel) / 2;
	if averageFuel < 10 {
		set averageFuel = averageFuel * 2;
	} else {
		sequential { println("hello"); }
	}
};
```
