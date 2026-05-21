import { useState, useEffect, useRef } from 'react'
import * as echarts from 'echarts'

const API_BASE = 'http://localhost:8080'
const WS_BASE = 'ws://localhost:8080/api/ws'

function useWebSocket(deviceName, onMessage) {
  const [connected, setConnected] = useState(false)
  const clientRef = useRef(null)

  useEffect(() => {
    if (!deviceName) return

    let stompClient = null
    let socket = null

    const connect = async () => {
      const { default: Stomp } = await import('stompjs')
      const { default: SockJS } = await import('sockjs-client')

      socket = new SockJS(WS_BASE)
      stompClient = Stomp.over(socket)
      stompClient.debug = () => {}
      clientRef.current = stompClient

      stompClient.connect({}, () => {
        setConnected(true)
        stompClient.subscribe('/topic/fermenter-status', (message) => {
          const data = JSON.parse(message.body)
          if (!deviceName || data.deviceName === deviceName) {
            onMessage(data)
          }
        })
      }, (error) => {
        console.error('WebSocket error:', error)
        setConnected(false)
      })
    }

    connect()

    return () => {
      if (stompClient) {
        stompClient.disconnect()
      }
      setConnected(false)
    }
  }, [deviceName, onMessage])

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
                        device.isOnline ? 'bg-green-500' : 'bg-gray-400'
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

    if (chartInstanceRef.current) {
      chartInstanceRef.current.dispose()
    }

    const chart = echarts.init(chartRef.current)
    chartInstanceRef.current = chart

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

    return () => chart.dispose()
  }, [data, title, color, unit])

  return <div ref={chartRef} className="w-full h-48" />
}

function DeviceDetail({ deviceName, status, realtimeData, wsConnected }) {
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
    { key: 'temperature', label: '温度', unit: '°C', color: '#EF4444', icon: '🌡️' },
    { key: 'phValue', label: 'pH值', unit: '', color: '#10B981', icon: '⚗️' },
    { key: 'dissolvedOxygen', label: '溶氧', unit: 'mg/L', color: '#3B82F6', icon: '💧' },
    { key: 'foamLevel', label: '泡沫液位', unit: '%', color: '#8B5CF6', icon: '🫧' },
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
          <h3 className="text-lg font-semibold text-gray-800 mb-4 flex items-center gap-2">
            <svg className="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            控制参数
          </h3>
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
            {controlCards.map(({ key, label, unit, color }) => (
              <div key={key} className="bg-gray-50 rounded-lg p-3">
                <div className="text-xs text-gray-500 mb-1">{label}</div>
                <div className="text-lg font-semibold" style={{ color }}>
                  {latestData?.[key]?.value?.toFixed(1) ?? '--'}
                  <span className="text-xs font-normal text-gray-400 ml-1">{unit}</span>
                </div>
                <div className="mt-2 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full transition-all duration-300"
                    style={{
                      width: `${Math.min((latestData?.[key]?.value ?? 0) * 2, 100)}%`,
                      backgroundColor: color,
                    }}
                  />
                </div>
              </div>
            ))}
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

  const handleStatusMessage = (data) => {
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
  }

  const wsConnected = useWebSocket(selectedDevice, handleStatusMessage)

  useEffect(() => {
    fetch(`${API_BASE}/api/fermenter-connection/devices`)
      .then(res => res.json())
      .then(data => setDevices(data))
      .catch(err => console.error('Failed to fetch devices:', err))
  }, [])

  useEffect(() => {
    if (!selectedDevice) {
      setStatus(null)
      setRealtimeData({ temperature: [], phValue: [], dissolvedOxygen: [], foamLevel: [] })
      return
    }

    fetch(`${API_BASE}/api/fermenter-status/${selectedDevice}/latest`)
      .then(res => res.json())
      .then(data => {
        setStatus(data)
        const now = data.timestamp ? new Date(data.timestamp).getTime() : Date.now()
        setRealtimeData({
          temperature: [{ value: data.temperature, timestamp: now }],
          phValue: [{ value: data.phValue, timestamp: now }],
          dissolvedOxygen: [{ value: data.dissolvedOxygen, timestamp: now }],
          foamLevel: [{ value: data.foamLevel, timestamp: now }],
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
      />
    </div>
  )
}

export default App