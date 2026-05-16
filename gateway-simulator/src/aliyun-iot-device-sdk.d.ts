declare module 'aliyun-iot-device-sdk' {
  interface DeviceConfig {
    productKey: string;
    deviceName: string;
    deviceSecret: string;
  }

  interface Device {
    on(event: 'connect', callback: () => void): void;
    on(event: 'error', callback: (err: Error) => void): void;
    on(event: 'command', callback: (command: any) => void): void;
    on(event: 'property', callback: (params: any) => void): void;
    postProps(properties: Record<string, any>): void;
    end(): void;
  }

  function device(config: DeviceConfig): Device;

  export = { device };
}