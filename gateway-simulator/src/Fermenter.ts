import AliyunIoT from 'aliyun-iot-device-sdk';

export interface FermenterProperties {
  temperature: number;
  phValue: number;
  dissolvedOxygen: number;
  foamLevel: number;
  addAcid: number;
  addAlkali: number;
  cooling: number;
  heating: number;
  stirring: number;
  controlMode: 0 | 1;
}

export interface FermenterConfig {
  deviceName: string;
  productKey: string;
  deviceSecret: string;
}

export class Fermenter {
  deviceName: string;
  productKey: string;
  deviceSecret: string;
  properties: FermenterProperties;
  client: any = null;
  connected: boolean = false;

  constructor(deviceName: string, productKey: string, deviceSecret: string) {
    this.deviceName = deviceName;
    this.productKey = productKey;
    this.deviceSecret = deviceSecret;
    this.properties = this.randomProperties();
  }

  private randomProperties(): FermenterProperties {
    return {
      temperature: this.randomRange(-10, 100),
      phValue: this.randomRange(0, 14),
      dissolvedOxygen: this.randomRange(0, 100),
      foamLevel: this.randomRange(0, 100),
      addAcid: 0,
      addAlkali: 0,
      cooling: 0,
      heating: 0,
      stirring: this.randomRange(30, 80),
      controlMode: 1,
    };
  }

  private randomRange(min: number, max: number): number {
    return Math.round((Math.random() * (max - min) + min) * 100) / 100;
  }
  private applyFuzzyControl(): void {
  const p = this.properties;

  //目标值
  const TARGET_TEMP = 30.0;
  const TARGET_PH = 7.0;

  //温度误差
  const tempError = p.temperature - TARGET_TEMP;
  const phError = p.phValue - TARGET_PH;

  // 温度模糊规则
  if (tempError > 2) {
    p.cooling = 1;
    p.heating = 0;
  } else if (tempError < -2) {
    p.heating = 1;
    p.cooling = 0;
  } else {
    p.heating = 0;
    p.cooling = 0;
  }

  //pH 模糊规则 
  if (phError > 0.2) {
    p.addAcid = 1;
    p.addAlkali = 0;
  } else if (phError < -0.2) {
    p.addAlkali = 1;
    p.addAcid = 0;
  } else {
    p.addAcid = 0;
    p.addAlkali = 0;
  }
}
  connect(): void {
    const deviceConfig = {
      productKey: this.productKey,
      deviceName: this.deviceName,
      deviceSecret: this.deviceSecret,
    };

    this.client = AliyunIoT.device(deviceConfig);

    this.client.on('connect', () => {
      console.log(`[${this.deviceName}] ✅ 成功连接到阿里云 IoT (MQTT)`);
      this.connected = true;
    });

    this.client.on('error', (err: Error) => {
      console.error(`[${this.deviceName}] Error:`, err.message);
      this.connected = false;
    });

    this.client.on('command', (command: any) => {
      console.log(`[${this.deviceName}] Received command:`, JSON.stringify(command));
      this.handleCommand(command);
    });

    this.client.on('property', (params: any) => {
      console.log(`[${this.deviceName}] Property set request:`, JSON.stringify(params));
      this.handlePropertySet(params);
    });
  }

  private handleCommand(command: any): void {
    if (command && command.command) {
      console.log(`[${this.deviceName}] Executing command: ${command.command}`);
    }
  }

  private handlePropertySet(params: any): void {
    if (params.addAcid !== undefined) this.properties.addAcid = params.addAcid;
    if (params.addAlkali !== undefined) this.properties.addAlkali = params.addAlkali;
    if (params.cooling !== undefined) this.properties.cooling = params.cooling;
    if (params.heating !== undefined) this.properties.heating = params.heating;
    if (params.stirring !== undefined) this.properties.stirring = params.stirring;
    if (params.controlMode !== undefined) this.properties.controlMode = params.controlMode;
  }

  reportProperties(): void {
    if (!this.connected || !this.client) {
      console.warn(`[${this.deviceName}] Cannot report properties: not connected`);
      return;
    }

    this.client.postProps({
      temperature: this.properties.temperature,
      phValue: this.properties.phValue,
      dissolvedOxygen: this.properties.dissolvedOxygen,
      foamLevel: this.properties.foamLevel,
      addAcid: this.properties.addAcid,
      addAlkali: this.properties.addAlkali,
      cooling: this.properties.cooling,
      heating: this.properties.heating,
      stirring: this.properties.stirring,
      controlMode: this.properties.controlMode,
    });
  }

  updateSensorValues(): void {
    this.properties.temperature += this.randomRange(-2, 2);
    this.properties.temperature = Math.max(-10, Math.min(100, this.properties.temperature));

    this.properties.phValue += this.randomRange(-0.2, 0.2);
    this.properties.phValue = Math.max(0, Math.min(14, this.properties.phValue));

    this.properties.dissolvedOxygen += this.randomRange(-5, 5);
    this.properties.dissolvedOxygen = Math.max(0, Math.min(100, this.properties.dissolvedOxygen));

    this.properties.foamLevel += this.randomRange(-3, 3);
    this.properties.foamLevel = Math.max(0, Math.min(100, this.properties.foamLevel));
    this.applyFuzzyControl();//每次传感器变化后自动决策
  }
  disconnect(): void {
    if (this.client) {
      this.client.end();
      this.connected = false;
      console.log(`[${this.deviceName}] Disconnected`);
    }
  }

  toJSON(): FermenterConfig & { connected: boolean; properties: FermenterProperties } {
    return {
      deviceName: this.deviceName,
      productKey: this.productKey,
      deviceSecret: this.deviceSecret,
      connected: this.connected,
      properties: { ...this.properties },
    };
  }
}