import inquirer from 'inquirer';
import chalk from 'chalk';
import * as dotenv from 'dotenv';
import * as path from 'path';
import { Fermenter, FermenterConfig } from './Fermenter.js';

dotenv.config({ path: path.resolve(process.cwd(), '.env') });

class GatewaySimulator {
  fermenters: Map<string, Fermenter> = new Map();
  updateInterval: ReturnType<typeof setInterval> | null = null;
  reportInterval: ReturnType<typeof setInterval> | null = null;
  productKey: string = process.env.PRODUCT_KEY || '';

  async start(): Promise<void> {
    console.log(chalk.cyan('='.repeat(50)));
    console.log(chalk.cyan('  发酵罐网关模拟器'));
    console.log(chalk.cyan('='.repeat(50)));
    if (this.productKey) {
      console.log(chalk.gray(`\n产品: ${this.productKey}\n`));
    } else {
      console.log(chalk.red('\n警告: 未设置 PRODUCT_KEY 和 PRODUCT_SECRET\n'));
    }

    await this.mainMenu();
  }

  async mainMenu(): Promise<void> {
    const choices = [
      { name: '添加发酵罐', value: 'add' },
      { name: '查看所有发酵罐', value: 'list' },
      { name: '操作发酵罐', value: 'operate' },
      { name: '启动所有设备连接', value: 'connect' },
      { name: '停止所有设备连接', value: 'disconnect' },
      { name: '保存配置', value: 'save' },
      { name: '加载配置', value: 'load' },
      { name: '退出', value: 'exit' },
    ];

    const { action } = await inquirer.prompt({
      type: 'list',
      name: 'action',
      message: '请选择操作:',
      choices,
    });

    switch (action) {
      case 'add':
        await this.addFermenter();
        break;
      case 'list':
        await this.listFermenters();
        break;
      case 'operate':
        await this.operateFermenter();
        break;
      case 'connect':
        this.connectAll();
        break;
      case 'disconnect':
        this.disconnectAll();
        break;
      case 'save':
        this.saveConfig();
        break;
      case 'load':
        this.loadFermentersConfig();
        break;
      case 'exit':
        this.exit();
        return;
    }

    await this.mainMenu();
  }

  async addFermenter(): Promise<void> {
    console.log(chalk.yellow('\n--- 添加新发酵罐 ---\n'));

    if (!this.productKey) {
      console.log(chalk.red('错误: 请先在 .env 中配置产品密钥\n'));
      return;
    }

    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'deviceName',
        message: `设备名称 (deviceName) [产品: ${this.productKey}]:`,
        validate: (v: string) => v.trim().length > 0 || '不能为空',
      },
      {
        type: 'input',
        name: 'deviceSecret',
        message: '设备密钥 (deviceSecret):',
        validate: (v: string) => v.trim().length > 0 || '不能为空',
      },
    ]);

    const { deviceName, deviceSecret } = answers;

    if (this.fermenters.has(deviceName)) {
      console.log(chalk.red(`错误: 设备 "${deviceName}" 已存在\n`));
      return;
    }

    const fermenter = new Fermenter(deviceName, this.productKey, deviceSecret);
    this.fermenters.set(deviceName, fermenter);
    console.log(chalk.green(`\n成功添加发酵罐: ${deviceName}\n`));
    console.log(chalk.gray('提示: 使用 "启动所有设备连接" 来连接阿里云IoT\n'));
  }

  async listFermenters(): Promise<void> {
    console.log(chalk.yellow('\n--- 发酵罐列表 ---\n'));

    if (this.fermenters.size === 0) {
      console.log(chalk.gray('暂无发酵罐\n'));
      return;
    }

    for (const [name, fermenter] of this.fermenters) {
      const status = fermenter.connected
        ? chalk.green('已连接')
        : chalk.red('未连接');
      const props = fermenter.properties;

      console.log(chalk.cyan(`\n【${name}】${status}`));
      console.log(`  产品: ${fermenter.productKey}`);
      console.log(chalk.gray('  属性:'));
      console.log(`    温度: ${props.temperature.toFixed(1)}°C`);
      console.log(`    pH值: ${props.phValue.toFixed(2)}`);
      console.log(`    溶氧: ${props.dissolvedOxygen.toFixed(1)}%`);
      console.log(`    泡沫: ${props.foamLevel.toFixed(1)}%`);
      console.log(`    搅拌: ${props.stirring.toFixed(1)}%`);
      console.log(`    加酸: ${props.addAcid.toFixed(1)}%`);
      console.log(`    加碱: ${props.addAlkali.toFixed(1)}%`);
      console.log(`    冷却: ${props.cooling.toFixed(1)}%`);
      console.log(`    加热: ${props.heating.toFixed(1)}%`);
      console.log(`    模式: ${props.controlMode === 0 ? 'LOCAL' : 'REMOTE'}`);
    }
    console.log();
  }

  async operateFermenter(): Promise<void> {
    if (this.fermenters.size === 0) {
      console.log(chalk.red('错误: 暂无发酵罐\n'));
      return;
    }

    const choices = Array.from(this.fermenters.keys()).map((name) => ({
      name,
      value: name,
    }));

    const { deviceName } = await inquirer.prompt({
      type: 'list',
      name: 'deviceName',
      message: '选择要操作的发酵罐:',
      choices,
    });

    const fermenter = this.fermenters.get(deviceName)!;
    await this.fermenterMenu(fermenter);
  }

  async fermenterMenu(fermenter: Fermenter): Promise<void> {
    const choices = [
      { name: '查看详细属性', value: 'view' },
      { name: '设置控制参数', value: 'set' },
      { name: '模拟传感器变化', value: 'simulate' },
      { name: '上报属性到云端', value: 'report' },
      { name: '连接/断开连接', value: 'toggle' },
      { name: '删除此发酵罐', value: 'delete' },
      { name: '返回', value: 'back' },
    ];

    const { action } = await inquirer.prompt({
      type: 'list',
      name: 'action',
      message: `发酵罐: ${fermenter.deviceName}`,
      choices,
    });

    switch (action) {
      case 'view':
        this.viewFermenterDetails(fermenter);
        break;
      case 'set':
        await this.setFermenterProperties(fermenter);
        break;
      case 'simulate':
        fermenter.updateSensorValues();
        console.log(chalk.green('\n传感器数值已更新\n'));
        break;
      case 'report':
        fermenter.reportProperties();
        console.log(chalk.green('\n已上报属性到云端\n'));
        break;
      case 'toggle':
        if (fermenter.connected) {
          fermenter.disconnect();
        } else {
          fermenter.connect();
        }
        break;
      case 'delete':
        this.deleteFermenter(fermenter.deviceName);
        return;
      case 'back':
        return;
    }

    await this.fermenterMenu(fermenter);
  }

  viewFermenterDetails(fermenter: Fermenter): void {
    console.log(chalk.yellow(`\n--- ${fermenter.deviceName} 详细信息 ---\n`));
    console.log(`设备名称: ${fermenter.deviceName}`);
    console.log(`产品密钥: ${fermenter.productKey}`);
    console.log(`连接状态: ${fermenter.connected ? '已连接' : '未连接'}`);
    console.log();
    console.log(chalk.gray('当前属性值:'));
    const p = fermenter.properties;
    console.log(`  温度 [temperature]: ${p.temperature.toFixed(1)} (-10~100)`);
    console.log(`  pH值 [phValue]: ${p.phValue.toFixed(2)} (0~14)`);
    console.log(`  溶氧 [dissolvedOxygen]: ${p.dissolvedOxygen.toFixed(1)}% (0~100)`);
    console.log(`  泡沫 [foamLevel]: ${p.foamLevel.toFixed(1)}% (0~100)`);
    console.log(`  加酸 [addAcid]: ${p.addAcid.toFixed(1)}% (0~100)`);
    console.log(`  加碱 [addAlkali]: ${p.addAlkali.toFixed(1)}% (0~100)`);
    console.log(`  冷却 [cooling]: ${p.cooling.toFixed(1)}% (0~100)`);
    console.log(`  加热 [heating]: ${p.heating.toFixed(1)}% (0~100)`);
    console.log(`  搅拌 [stirring]: ${p.stirring.toFixed(1)}% (0~100)`);
    console.log(`  控制模式 [controlMode]: ${p.controlMode === 0 ? 'LOCAL' : 'REMOTE'}`);
    console.log();
  }

  async setFermenterProperties(fermenter: Fermenter): Promise<void> {
    console.log(chalk.yellow('\n--- 设置控制参数 ---\n'));
    console.log('输入新值 (直接回车保持当前值)\n');

    const current = fermenter.properties;
    const answers = await inquirer.prompt([
      {
        type: 'input',
        name: 'addAcid',
        message: `加酸 (当前: ${current.addAcid.toFixed(1)}):`,
      },
      {
        type: 'input',
        name: 'addAlkali',
        message: `加碱 (当前: ${current.addAlkali.toFixed(1)}):`,
      },
      {
        type: 'input',
        name: 'cooling',
        message: `冷却 (当前: ${current.cooling.toFixed(1)}):`,
      },
      {
        type: 'input',
        name: 'heating',
        message: `加热 (当前: ${current.heating.toFixed(1)}):`,
      },
      {
        type: 'input',
        name: 'stirring',
        message: `搅拌 (当前: ${current.stirring.toFixed(1)}):`,
      },
      {
        type: 'list',
        name: 'controlMode',
        message: `控制模式 (当前: ${current.controlMode === 0 ? 'LOCAL' : 'REMOTE'}):`,
        choices: [
          { name: 'LOCAL - 本地控制', value: 0 },
          { name: 'REMOTE - 远程控制', value: 1 },
        ],
      },
    ]);

    if (answers.addAcid !== '') fermenter.properties.addAcid = parseFloat(answers.addAcid);
    if (answers.addAlkali !== '') fermenter.properties.addAlkali = parseFloat(answers.addAlkali);
    if (answers.cooling !== '') fermenter.properties.cooling = parseFloat(answers.cooling);
    if (answers.heating !== '') fermenter.properties.heating = parseFloat(answers.heating);
    if (answers.stirring !== '') fermenter.properties.stirring = parseFloat(answers.stirring);
    fermenter.properties.controlMode = answers.controlMode;

    console.log(chalk.green('\n控制参数已更新\n'));
  }

  deleteFermenter(deviceName: string): void {
    const fermenter = this.fermenters.get(deviceName);
    if (fermenter) {
      fermenter.disconnect();
      this.fermenters.delete(deviceName);
      console.log(chalk.green(`\n已删除发酵罐: ${deviceName}\n`));
    }
  }

  connectAll(): void {
    if (this.fermenters.size === 0) {
      console.log(chalk.red('错误: 暂无发酵罐\n'));
      return;
    }

    console.log(chalk.yellow('\n正在连接所有设备到阿里云IoT...\n'));

    for (const fermenter of this.fermenters.values()) {
      if (!fermenter.connected) {
        fermenter.connect();
      }
    }

    if (this.updateInterval) clearInterval(this.updateInterval);
    if (this.reportInterval) clearInterval(this.reportInterval);

    this.updateInterval = setInterval(() => {
      for (const fermenter of this.fermenters.values()) {
        if (fermenter.connected) {
          fermenter.updateSensorValues();
        }
      }
    }, 5000);

    this.reportInterval = setInterval(() => {
      for (const fermenter of this.fermenters.values()) {
        if (fermenter.connected) {
          fermenter.reportProperties();
        }
      }
    }, 10000);

    console.log(chalk.green('已启动自动更新 (每5秒更新传感器, 每10秒上报云端)\n'));
  }

  disconnectAll(): void {
    for (const fermenter of this.fermenters.values()) {
      fermenter.disconnect();
    }

    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = null;
    }
    if (this.reportInterval) {
      clearInterval(this.reportInterval);
      this.reportInterval = null;
    }

    console.log(chalk.yellow('\n已断开所有设备连接\n'));
  }

  saveConfig(): void {
    const fs = require('fs');
    const configs: FermenterConfig[] = [];
    for (const fermenter of this.fermenters.values()) {
      configs.push({
        deviceName: fermenter.deviceName,
        productKey: fermenter.productKey,
        deviceSecret: fermenter.deviceSecret,
      });
    }

    fs.writeFileSync('./fermenters-config.json', JSON.stringify(configs, null, 2));
    console.log(chalk.green('\n配置已保存到: ./fermenters-config.json\n'));
  }

  loadFermentersConfig(): void {
    const fs = require('fs');
    const configPath = './fermenters-config.json';

    if (!fs.existsSync(configPath)) {
      console.log(chalk.red(`\n配置文件不存在: ${configPath}\n`));
      return;
    }

    try {
      const configs: FermenterConfig[] = JSON.parse(fs.readFileSync(configPath, 'utf-8'));

      for (const config of configs) {
        if (!this.fermenters.has(config.deviceName)) {
          const fermenter = new Fermenter(
            config.deviceName,
            config.productKey,
            config.deviceSecret
          );
          this.fermenters.set(config.deviceName, fermenter);
        }
      }

      console.log(chalk.green(`\n已加载 ${configs.length} 个发酵罐配置\n`));
    } catch (e) {
      console.log(chalk.red(`\n加载配置失败: ${e}\n`));
    }
  }

  exit(): void {
    this.disconnectAll();
    console.log(chalk.cyan('\n再见!\n'));
    process.exit(0);
  }
}

const gateway = new GatewaySimulator();
gateway.start().catch(console.error);