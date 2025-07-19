package com.example.bluetooth_blink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bluetooth_blink.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private var connectedDevice: BluetoothDevice? = null
    
    // LED와 모터 상태 관리
    private var led1State = false
    private var led2State = false
    private var led3State = false
    private var motorState = false
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
        initializeBluetooth()
    }

    private fun setupUI() {
        binding.apply {
            var command = ""
            // LED 토글 스위치들
            switchLed1.setOnCheckedChangeListener { _, isChecked ->
                led1State = isChecked
                updateLedIcon(1, isChecked)
                command = if (isChecked) "r1" else "r0"
                sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"${command}\"}\n")
            }
            
            switchLed2.setOnCheckedChangeListener { _, isChecked ->
                led2State = isChecked
                updateLedIcon(2, isChecked)
                command = if (isChecked) "y1" else "y0"
                sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"${command}\"}\n")
            }
            
            switchLed3.setOnCheckedChangeListener { _, isChecked ->
                led3State = isChecked
                updateLedIcon(3, isChecked)
                command = if (isChecked) "g1" else "g0"
                sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"${command}\"}\n")
            }
            
            // 모터 토글 스위치
            switchMotor.setOnClickListener {
                motorState = true
                updateMotorIcon(true)

                coroutineScope.launch(Dispatchers.IO) {
                    sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"m1\"}\n")

                    delay(500)

                    // UI 스레드에서 updateMotorIcon(false) 실행
                    withContext(Dispatchers.Main) {
                        updateMotorIcon(false)
                    }

                    sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"m0\"}\n")
                }
            }
            
            // 연결 관리 버튼들
            btnConnect.setOnClickListener { showPairedDevicesDialog() }
            btnDisconnect.setOnClickListener { disconnectDevice() }
            btnScan.setOnClickListener { scanDevices() }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    private fun initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "블루투스가 지원되지 않는 기기입니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPairedDevicesDialog() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "블루투스가 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "블루투스를 활성화해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = bluetoothAdapter!!.bondedDevices
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "페어링된 디바이스가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = pairedDevices.map { it.name ?: it.address }.toTypedArray()
        val devices = pairedDevices.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("페어링된 디바이스 선택")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = devices[which]
                connectToDevice(selectedDevice)
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun connectToDevice(device: BluetoothDevice) {
        appendToLog("연결 시도: ${device.name ?: device.address}")
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // 기존 연결이 있으면 해제
                bluetoothSocket?.close()
                
                // 새로운 연결 시도
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()
                
                outputStream = bluetoothSocket?.outputStream
                
                withContext(Dispatchers.Main) {
                    isConnected = true
                    connectedDevice = device
                    updateConnectionStatus()
                    Toast.makeText(this@MainActivity, "연결 성공: ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                    appendToLog("연결 성공: ${device.name ?: device.address}")
                    
                    // 연결 성공 시 모든 LED와 모터를 끄고 초기화
                    initializeAllDevices()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "연결 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    appendToLog("연결 실패: ${e.message}")
                    isConnected = false
                    connectedDevice = null
                    updateConnectionStatus()
                }
            }
        }
    }

    private fun disconnectDevice() {
        if (!isConnected) {
            Toast.makeText(this, "연결된 디바이스가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                outputStream?.close()
                bluetoothSocket?.close()
                withContext(Dispatchers.Main) {
                    isConnected = false
                    connectedDevice = null
                    updateConnectionStatus()
                    Toast.makeText(this@MainActivity, "연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
                    appendToLog("연결 해제됨")
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "연결 해제 중 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    appendToLog("연결 해제 오류: ${e.message}")
                }
            }
        }
    }

    private fun scanDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "블루투스가 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "블루투스를 활성화해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        appendToLog("블루투스 스캔 시작...")
        
        // 페어링된 디바이스 목록 표시
        val pairedDevices = bluetoothAdapter!!.bondedDevices
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "페어링된 디바이스가 없습니다.", Toast.LENGTH_SHORT).show()
            appendToLog("페어링된 디바이스 없음")
            return
        }

        val deviceList = StringBuilder()
        deviceList.append("발견된 페어링된 디바이스:\n")
        
        pairedDevices.forEach { device ->
            val deviceInfo = "${device.name ?: "Unknown"} (${device.address})"
            deviceList.append("• $deviceInfo\n")
        }
        
        appendToLog(deviceList.toString())
        
        AlertDialog.Builder(this)
            .setTitle("발견된 블루투스 디바이스")
            .setMessage(deviceList.toString())
            .setPositiveButton("연결하기") { _, _ ->
                showPairedDevicesDialog()
            }
            .setNegativeButton("닫기") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun sendCommand(command: String) {
        if (!isConnected) {
            Toast.makeText(this, "먼저 블루투스 디바이스에 연결해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write("$command\n".toByteArray())
                outputStream?.flush()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "명령 전송: $command", Toast.LENGTH_SHORT).show()
                    appendToLog("전송: $command")
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "명령 전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    appendToLog("오류: $command 전송 실패 - ${e.message}")
                    // 연결이 끊어진 것으로 간주
                    isConnected = false
                    connectedDevice = null
                    updateConnectionStatus()
                }
            }
        }
    }

    private fun updateConnectionStatus() {
        binding.tvConnectionStatus.text = if (isConnected) {
            "연결됨: ${connectedDevice?.name ?: connectedDevice?.address ?: "Unknown"}"
        } else {
            "연결 안됨"
        }
        binding.tvConnectionStatus.setTextColor(
            ContextCompat.getColor(this, if (isConnected) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
    }

    private fun appendToLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.tvLog.append("[$timestamp] $message\n")
        
        // 스크롤을 맨 아래로
        val scrollAmount = binding.tvLog.layout.getLineTop(binding.tvLog.lineCount) - binding.tvLog.height
        if (scrollAmount > 0) {
            binding.tvLog.scrollTo(0, scrollAmount)
        }
    }

    private fun initializeAllDevices() {
        // 모든 스위치를 끄고 상태 초기화
        binding.switchLed1.isChecked = false
        binding.switchLed2.isChecked = false
        binding.switchLed3.isChecked = false
        
        led1State = false
        led2State = false
        led3State = false
        motorState = false
        
        // 모든 아이콘을 꺼진 상태로 업데이트
        updateLedIcon(1, false)
        updateLedIcon(2, false)
        updateLedIcon(3, false)
        updateMotorIcon(false)
        
        // 모든 디바이스를 끄는 명령 전송

        coroutineScope.launch(Dispatchers.IO) {
            sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"r0\"}\n")
            delay(50)
            sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"y0\"}\n")
            delay(50)
            sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"g0\"}\n")
            delay(50)
            sendCommand("{\"MESSAGE_TYPE_LOCAL\":\"test\",\"COMMAND\":\"m0\"}\n")
            delay(50)
        }
        
        appendToLog("모든 디바이스 초기화 완료 (OFF 상태)")
    }
    
    private fun updateLedIcon(ledNumber: Int, isOn: Boolean) {
        val iconRes = when {
            ledNumber == 1 && isOn -> R.drawable.ic_led_red
            ledNumber == 2 && isOn -> R.drawable.ic_led_yellow
            ledNumber == 3 && isOn -> R.drawable.ic_led_green
            else -> R.drawable.ic_led_off
        }
        
        when (ledNumber) {
            1 -> binding.ivLed1.setImageResource(iconRes)
            2 -> binding.ivLed2.setImageResource(iconRes)
            3 -> binding.ivLed3.setImageResource(iconRes)
        }
    }
    
    private fun updateMotorIcon(isOn: Boolean) {
        val iconRes = if (isOn) R.drawable.ic_motor_on else R.drawable.ic_motor_off
        binding.ivMotor.setImageResource(iconRes)
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectDevice()
        coroutineScope.cancel()
    }
}