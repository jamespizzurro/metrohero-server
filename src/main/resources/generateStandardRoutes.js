// replace "MY_API_KEY" with your WMATA API key,
// then run this in your browser's developer console, copy the output, and replace all of StandardRoutes.json with that output

(async () => {
  const response = await fetch("https://api.wmata.com/TrainPositions/StandardRoutes?contentType=json", {
    headers: {
      "api_key": "MY_API_KEY"
    }
  });
  const data = await response.json();
  for (const standardRoute of data.StandardRoutes) {
    const numReferencesByCircuitId = {};
    for (const trackCircuit of standardRoute.TrackCircuits) {
      numReferencesByCircuitId[trackCircuit.CircuitId] = (numReferencesByCircuitId[trackCircuit.CircuitId] || 0) + 1;
    }

    standardRoute.TrackCircuits.sort((a, b) => a.SeqNum - b.SeqNum);

    for (const [key, value] of Object.entries(numReferencesByCircuitId)) {
      if (value > 1) {
        console.warn(`duplicate circuit ID in ${standardRoute.LineCode} track ${standardRoute.TrackNum} configuration: circuit ${key} appears ${value} times`);
      }
    }
  }

  console.log(data);
})();
