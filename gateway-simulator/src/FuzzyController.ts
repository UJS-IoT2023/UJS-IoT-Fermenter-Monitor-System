export interface SensorReadings {
  temperature: number;
  phValue: number;
}

export interface ControlOutputs {
  addAcid: number;
  addAlkali: number;
  cooling: number;
  heating: number;
  stirring: number;
}

export class FuzzyController {
  private levels = [0, 25, 50, 75, 100] as const;

  adjustControls(sensors: SensorReadings): ControlOutputs {
    let addAcid = 50;
    let addAlkali = 50;

    if (sensors.phValue > 7.5) {
      addAcid = Math.min(100, addAcid + 25);
      addAlkali = Math.max(0, addAlkali - 25);
    } else if (sensors.phValue > 8.0) {
      addAcid = Math.min(100, addAcid + 50);
      addAlkali = Math.max(0, addAlkali - 50);
    } else if (sensors.phValue < 6.5) {
      addAcid = Math.max(0, addAcid - 25);
      addAlkali = Math.min(100, addAlkali + 25);
    } else if (sensors.phValue < 6.0) {
      addAcid = Math.max(0, addAcid - 50);
      addAlkali = Math.min(100, addAlkali + 50);
    }

    let cooling = 50;
    let heating = 50;

    if (sensors.temperature > 40) {
      cooling = Math.min(100, cooling + 25);
      heating = Math.max(0, heating - 25);
    } else if (sensors.temperature > 45) {
      cooling = Math.min(100, cooling + 50);
      heating = Math.max(0, heating - 50);
    } else if (sensors.temperature < 30) {
      cooling = Math.max(0, cooling - 25);
      heating = Math.min(100, heating + 25);
    } else if (sensors.temperature < 25) {
      cooling = Math.max(0, cooling - 50);
      heating = Math.min(100, heating + 50);
    }

    const stirring = 50;

    return { addAcid, addAlkali, cooling, heating, stirring };
  }
}
