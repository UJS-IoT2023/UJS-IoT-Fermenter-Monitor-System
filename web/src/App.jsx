import { useState, useEffect, useRef, useCallback } from 'react'
import * as echarts from 'echarts'

const API_BASE = 'http://localhost:8080'
const WS_BASE = 'ws://localhost:8080/api/ws'

function useWebSocket(onMessage) {
  const [connected, setConnected] = useState(false)
  const clientRef = useRef(null)
  const onMessageRef = useRef()
  const clientClassRef = useRef(null)
  const effectStarted = useRef(false)

  useEffect(() => {
    import('@stomp/stompjs').then(({ Client }) => {
      clientClassRef.current = Client
      if (effectStarted.current) return
      effectStarted.current = true

      const stompClient = new Client({
        brokerURL: WS_BASE,
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
      })

      clientRef.current = stompClient

      stompClient.onConnect = () => {
        setConnected(true)
        stompClient.subscribe('/topic/fermenter-status', (message) => {
          if (message.body) {
            onMessageRef.current(JSON.parse(message.body))
          }
        })
      }

      stompClient.onStompError = (frame) => {
        console.error('STOMP error:', frame.headers['message'])
        setConnected(false)
      }

      stompClient.activate()
    })
  }, [])

  useEffect(() => {
    onMessageRef.current = onMessage
  }, [onMessage])

  return connected
}

function DeviceList({ devices, selectedDevice, onSelect }) {
  return (
    <div className="w-80 border-r border-gray-200 flex flex-col bg-white">
      <div className="p-4 border-b border-gray-200 bg-gray-50">
        <h2 className="text-lg font-semibold text-gray-800 flex items-center gap-2">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
          </svg>
          设备列表
        </h2>
      </div>
      <div className="flex-1 overflow-y-auto">
        {devices.length === 0 ? (
          <div className="p-4 text-gray-500 text-center">暂无设备</div>
        ) : (
          <ul className="divide-y divide-gray-100">
            {devices.map((device) => (
              <li key={device.deviceName}>
                <button
                  onClick={() => onSelect(device.deviceName)}
                  className={`w-full p-4 text-left transition-all duration-200 hover:bg-blue-50 ${
                    selectedDevice === device.deviceName
                      ? 'bg-blue-100 border-l-4 border-blue-500'
                      : ''
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-gray-800">{device.deviceName}</span>
                    <span
                      className={`w-2.5 h-2.5 rounded-full ${
                        device.online ? 'bg-green-500' : 'bg-gray-400'
                      }`}
                    />
                  </div>
                  {device.lastTime && (
                    <div className="text-xs text-gray-500 mt-1">
                      {device.lastTime}
                    </div>
                  )}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function RealtimeChart({ data, title, color, unit }) {
  const chartRef = useRef(null)
  const chartInstanceRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) return

    if (!chartInstanceRef.current) {
      chartInstanceRef.current = echarts.init(chartRef.current)
    }

    const chart = chartInstanceRef.current

    const option = {
      title: {
        text: title,
        textStyle: { fontSize: 14, fontWeight: 500, color: '#374151' },
        left: 'center',
        top: 8,
      },
      tooltip: {
        trigger: 'axis',
        formatter: `{b}<br/>{c} ${unit}`,
      },
      grid: { left: 50, right: 20, top: 40, bottom: 30 },
      xAxis: {
        type: 'category',
        data: data.map(d => new Date(d.timestamp).toLocaleTimeString()),
        axisLabel: { fontSize: 10, color: '#6B7280' },
      },
      yAxis: {
        type: 'value',
        axisLabel: { fontSize: 10, color: '#6B7280' },
        splitLine: { lineStyle: { color: '#F3F4F6' } },
      },
      series: [
        {
          data: data.map(d => d.value),
          type: 'line',
          smooth: true,
          lineStyle: { color, width: 2 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: color + '40' },
              { offset: 1, color: color + '10' },
            ]),
          },
          symbol: 'circle',
          symbolSize: 4,
          itemStyle: { color },
        },
      ],
    }

    chart.setOption(option)

    return () => {
      if (chartInstanceRef.current) {
        chartInstanceRef.current.dispose()
        chartInstanceRef.current = null
      }
    }
  }, [title, color, unit])

  useEffect(() => {
    if (!chartInstanceRef.current) return
    chartInstanceRef.current.setOption({
      xAxis: {
        type: 'category',
        data: data.map(d => new Date(d.timestamp).toLocaleTimeString()),
      },
      series: [
        {
          data: data.map(d => d.value),
        },
      ],
    })
  }, [data])

  return <div ref={chartRef} className="w-full h-48" />
}

function DeviceDetail({ deviceName, status, realtimeData, wsConnected, controlValues, onControlChange, onControlModeChange }) {
  if (!deviceName) {
    return (
      <div className="flex-1 flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <svg className="w-16 h-16 mx-auto text-gray-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          <p className="text-gray-500 text-lg">请选择设备</p>
          <p className="text-gray-400 text-sm mt-2">从左侧列表选择一个设备查看详情</p>
        </div>
      </div>
    )
  }

  const latestData = status ? {
    temperature: { value: status.temperature, timestamp: status.timestamp },
    phValue: { value: status.phValue, timestamp: status.timestamp },
    dissolvedOxygen: { value: status.dissolvedOxygen, timestamp: status.timestamp },
    foamLevel: { value: status.foamLevel, timestamp: status.timestamp },
  } : null

  const paramCards = [
    { key: 'temperature', label: '温度', unit: '°C', color: '#EF4444' },
    { key: 'phValue', label: 'pH值', unit: '', color: '#10B981' },
    { key: 'dissolvedOxygen', label: '溶氧', unit: 'mg/L', color: '#3B82F6' },
    { key: 'foamLevel', label: '泡沫液位', unit: '%', color: '#8B5CF6' },
  ]

  const controlCards = [
    { key: 'addAcid', label: '加酸', unit: '%', color: '#F59E0B' },
    { key: 'addAlkali', label: '加碱', unit: '%', color: '#6366F1' },
    { key: 'cooling', label: '冷却', unit: '%', color: '#06B6D4' },
    { key: 'heating', label: '加热', unit: '%', color: '#EC4899' },
    { key: 'stirring', label: '搅拌', unit: '%', color: '#84CC16' },
  ]

  return (
    <div className="flex-1 overflow-y-auto bg-gray-50 p-6">
      <div className="max-w-7xl mx-auto">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{deviceName}</h1>
            <p className="text-gray-500 text-sm mt-1">实时监控</p>
          </div>
          <div className="flex items-center gap-2">
            <span className={`w-2 h-2 rounded-full ${wsConnected ? 'bg-green-500 animate-pulse' : 'bg-gray-400'}`} />
            <span className="text-sm text-gray-500">{wsConnected ? '实时连接' : '连接中...'}</span>
          </div>
        </div>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          {paramCards.map(({ key, label, unit, color, icon }) => (
            <div key={key} className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
              <div className="flex items-center justify-between mb-2">
                <span className="text-2xl">{icon}</span>
                <span className="text-xs text-gray-500">{label}</span>
              </div>
              <div className="text-2xl font-bold" style={{ color }}>
                {latestData?.[key]?.value?.toFixed(1) ?? '--'}
                <span className="text-sm font-normal text-gray-400 ml-1">{unit}</span>
              </div>
            </div>
          ))}
        </div>

        <div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-lg font-semibold text-gray-800 flex items-center gap-2">
              <svg className="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              控制参数
            </h3>
            <div className="flex items-center gap-3">
              <span className="text-sm text-gray-500">控制模式</span>
              <div className="flex rounded-lg overflow-hidden border border-gray-200">
                <button
                  onClick={() => onControlModeChange(0)}
                  className={`px-3 py-1 text-sm font-medium transition-colors ${
                    (status?.controlMode === 0 || status?.controlMode === 'LOCAL')
                      ? 'bg-green-500 text-white'
                      : 'bg-white text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  本地自动
                </button>
                <button
                  onClick={() => onControlModeChange(1)}
                  className={`px-3 py-1 text-sm font-medium transition-colors ${
                    (status?.controlMode === 1 || status?.controlMode === 'REMOTE')
                      ? 'bg-blue-500 text-white'
                      : 'bg-white text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  远程控制
                </button>
              </div>
            </div>
          </div>

          {((status?.controlMode === 0 || status?.controlMode === 'LOCAL')) && (
            <div className="mb-3 px-3 py-2 bg-green-50 border border-green-200 rounded-lg">
              <p className="text-sm text-green-700">本地自动控制中，参数由网关模糊控制器自动调整</p>
            </div>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">
            {[
              { key: 'addAcid', label: '加酸', color: '#F59E0B' },
              { key: 'addAlkali', label: '加碱', color: '#6366F1' },
              { key: 'cooling', label: '冷却', color: '#06B6D4' },
              { key: 'heating', label: '加热', color: '#EC4899' },
              { key: 'stirring', label: '搅拌', color: '#84CC16' },
            ].map(({ key, label, color }) => {
              const isLocal = status?.controlMode === 0 || status?.controlMode === 'LOCAL';
              const currentValue = controlValues[key] ?? 0;
              return (
                <div key={key} className="bg-gray-50 rounded-lg p-3">
                  <div className="flex justify-between text-xs text-gray-500 mb-1">
                    <span>{label}</span>
                    <span className="font-semibold" style={{ color }}>{currentValue}%</span>
                  </div>
                  <input
                    type="range"
                    min="0" max="100" step="25"
                    value={currentValue}
                    onChange={(e) => onControlChange(key, Number(e.target.value))}
                    disabled={isLocal}
                    className="w-full h-2 rounded-lg appearance-none cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                    style={{ accentColor: color }}
                  />
                  <div className="flex justify-between text-xs text-gray-400 mt-0.5">
                    <span>0</span><span>25</span><span>50</span><span>75</span><span>100</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
            <RealtimeChart
              data={realtimeData.temperature}
              title="温度趋势"
              color="#EF4444"
              unit="°C"
            />
          </div>
          <div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
            <RealtimeChart
              data={realtimeData.phValue}
              title="pH值趋势"
              color="#10B981"
              unit=""
            />
          </div>
          <div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
            <RealtimeChart
              data={realtimeData.dissolvedOxygen}
              title="溶氧趋势"
              color="#3B82F6"
              unit="mg/L"
            />
          </div>
          <div className="bg-white rounded-xl p-4 shadow-sm border border-gray-100">
            <RealtimeChart
              data={realtimeData.foamLevel}
              title="泡沫液位趋势"
              color="#8B5CF6"
              unit="%"
            />
          </div>
        </div>

        {status?.controlMode && (
          <div className="mt-6 bg-white rounded-xl p-4 shadow-sm border border-gray-100">
            <div className="flex items-center gap-4">
              <span className="text-gray-500">控制模式</span>
              <span className="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm font-medium">
                {status.controlMode === 'AUTO' ? '自动' : status.controlMode === 'MANUAL' ? '手动' : status.controlMode}
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function App() {
  const [devices, setDevices] = useState([])
  const [selectedDevice, setSelectedDevice] = useState(null)
  const [status, setStatus] = useState(null)
  const [realtimeData, setRealtimeData] = useState({
    temperature: [],
    phValue: [],
    dissolvedOxygen: [],
    foamLevel: [],
  })
  const [controlValues, setControlValues] = useState({
    addAcid: 0, addAlkali: 0, cooling: 0, heating: 0, stirring: 0,
  })

  const onControlChange = useCallback((key, value) => {
    setControlValues(prev => ({ ...prev, [key]: value }))
    if (!selectedDevice) return
    fetch(`${API_BASE}/api/fermenter-control/command`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceName: selectedDevice, [key]: value }),
    }).catch(err => console.error('Failed to send control command:', err))
  }, [selectedDevice])

  const onControlModeChange = useCallback((mode) => {
    if (!selectedDevice) return
    fetch(`${API_BASE}/api/fermenter-control/command`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceName: selectedDevice, controlMode: mode }),
    }).catch(err => console.error('Failed to send control mode change:', err))
  }, [selectedDevice])

  const handleStatusMessage = useCallback((data) => {
    setStatus(data)

    setRealtimeData(prev => {
      const addPoint = (arr, value, timestamp) => [
        ...arr.slice(-59),
        { value, timestamp: timestamp || Date.now() },
      ]

      return {
        temperature: addPoint(prev.temperature, data.temperature, data.timestamp),
        phValue: addPoint(prev.phValue, data.phValue, data.timestamp),
        dissolvedOxygen: addPoint(prev.dissolvedOxygen, data.dissolvedOxygen, data.timestamp),
        foamLevel: addPoint(prev.foamLevel, data.foamLevel, data.timestamp),
      }
    })
  }, [])

  const wsConnected = useWebSocket(handleStatusMessage)

  useEffect(() => {
    fetch(`${API_BASE}/api/fermenter-connection/devices`)
      .then(res => res.json())
      .then(data => setDevices(data))
      .catch(err => console.error('Failed to fetch devices:', err))
  }, [])

  useEffect(() => {
    if (!selectedDevice) {
      queueMicrotask(() => {
        setStatus(null)
        setRealtimeData({ temperature: [], phValue: [], dissolvedOxygen: [], foamLevel: [] })
        setControlValues({ addAcid: 0, addAlkali: 0, cooling: 0, heating: 0, stirring: 0 })
      })
      return
    }

    fetch(`${API_BASE}/api/fermenter-status/${selectedDevice}/last-20-minutes`)
      .then(res => res.json())
      .then(data => {
        if (!Array.isArray(data) || data.length === 0) {
          setStatus(null)
          setRealtimeData({ temperature: [], phValue: [], dissolvedOxygen: [], foamLevel: [] })
          return
        }
        const latest = data[data.length - 1]
        const timestamp = latest.timestamp ? new Date(latest.timestamp).getTime() : Date.now()
        setStatus(latest)
        setControlValues({
          addAcid: latest.addAcid ?? 0,
          addAlkali: latest.addAlkali ?? 0,
          cooling: latest.cooling ?? 0,
          heating: latest.heating ?? 0,
          stirring: latest.stirring ?? 0,
        })
        setRealtimeData({
          temperature: data.map(d => ({ value: d.temperature, timestamp: d.timestamp ? new Date(d.timestamp).getTime() : Date.now() })),
          phValue: data.map(d => ({ value: d.phValue, timestamp: d.timestamp ? new Date(d.timestamp).getTime() : Date.now() })),
          dissolvedOxygen: data.map(d => ({ value: d.dissolvedOxygen, timestamp: d.timestamp ? new Date(d.timestamp).getTime() : Date.now() })),
          foamLevel: data.map(d => ({ value: d.foamLevel, timestamp: d.timestamp ? new Date(d.timestamp).getTime() : Date.now() })),
        })
      })
      .catch(err => console.error('Failed to fetch status:', err))
  }, [selectedDevice])

  return (
    <div className="h-screen flex">
      <DeviceList
        devices={devices}
        selectedDevice={selectedDevice}
        onSelect={setSelectedDevice}
      />
      <DeviceDetail
        deviceName={selectedDevice}
        status={status}
        realtimeData={realtimeData}
        wsConnected={wsConnected}
        controlValues={controlValues}
        onControlChange={onControlChange}
        onControlModeChange={onControlModeChange}
      />
    </div>
  )
}

export default App