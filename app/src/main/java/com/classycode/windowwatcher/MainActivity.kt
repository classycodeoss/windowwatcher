package com.classycode.windowwatcher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classycode.windowwatcher.ui.theme.GrayTranslucent
import com.classycode.windowwatcher.ui.theme.GreenTranslucent
import com.classycode.windowwatcher.ui.theme.OfficeTheme
import com.classycode.windowwatcher.ui.theme.RedTranslucent
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "Office"
    }

    // UI state
    private val uiState = mutableStateOf(
        UiState(
            connected = false, statusMessage = "Initial",
            window1Open = null, window2Open = null, window3Open = null, window4Open = null,
            messageCount = 0
        )
    )

    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var mainHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // see: https://developer.android.com/training/scheduling/wakelock#screen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mainHandler = Handler(Looper.getMainLooper())
        mqttClient = MqttAndroidClient(this, Config.brokerUrl, Config.clientId)
        mqttClient.setCallback(object : MqttCallback {

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                message?.payload?.let { bytes ->
                    val payloadStr = String(bytes, StandardCharsets.UTF_8)
                    val jsonObject = JSONObject(payloadStr)

                    val endDeviceIdsObject = jsonObject.getJSONObject("end_device_ids")
                    val deviceId = endDeviceIdsObject.getString("device_id")
                    val uplinkMessageObject = jsonObject.getJSONObject("uplink_message")
                    val decodedPayloadObject = uplinkMessageObject.getJSONObject("decoded_payload")
                    if (!decodedPayloadObject.has("digital")) {
                        Log.w(
                            TAG,
                            "Ignoring uplink message for device $deviceId (no digital payload)"
                        )
                        return
                    }
                    val sensorPayload = decodedPayloadObject.toString()
                    Log.i(TAG, "messageArrived: deviceId=$deviceId, payload=$sensorPayload")

                    val newMessageCount = uiState.value.messageCount + 1
                    when (deviceId) {
                        Config.devices[0] -> {
                            uiState.value = uiState.value.copy(
                                window1Open = decodedPayloadObject.getInt("digital") == 0,
                                messageCount = newMessageCount
                            )
                        }
                        Config.devices[1] -> {
                            uiState.value = uiState.value.copy(
                                window2Open = decodedPayloadObject.getInt("digital") == 0,
                                messageCount = newMessageCount
                            )
                        }
                        Config.devices[2] -> {
                            uiState.value = uiState.value.copy(
                                window3Open = decodedPayloadObject.getInt("digital") == 0,
                                messageCount = newMessageCount
                            )
                        }
                        Config.devices[3] -> {
                            uiState.value = uiState.value.copy(
                                window4Open = decodedPayloadObject.getInt("digital") == 0,
                                messageCount = newMessageCount
                            )
                        }
                    }
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.e(TAG, "connectionLost")

                uiState.value = uiState.value.copy(
                    statusMessage = "Connection lost",
                    connected = false
                )

                // automatic reconnect (the built-in reconnect doesn't seem to work?)
                connect()
            }
        })

        setContent {
            OfficeTheme {
                val uiState by uiState
                Surface(color = MaterialTheme.colors.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainContent(uiState = uiState)
                    }
                }
            }
        }

        connect()
    }

    private fun connect() {
        val options = MqttConnectOptions()
        options.userName = Config.brokerUsername
        options.password = Config.brokerPassword.toCharArray()
        options.connectionTimeout = 10000
        mqttClient.connect(options, null, object : IMqttActionListener {

            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(TAG, "Connection succeeded")

                uiState.value = uiState.value.copy(
                    statusMessage = "Connected",
                    connected = true
                )
                subscribe()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e(TAG, "Connection failed", exception)
                uiState.value = uiState.value.copy(
                    statusMessage = "Connection failed",
                    connected = false
                )
                mainHandler.postDelayed({ connect() }, 10000)
            }
        })
    }

    override fun onDestroy() {
        unsubscribe()
        mqttClient.disconnect()
        super.onDestroy()
    }

    private fun subscribe() {
        val qos = intArrayOf(1, 1, 1, 1)
        val topics = Config.topics.toTypedArray()
        mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {

            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(TAG, "Subscribe succeeded")
                uiState.value = uiState.value.copy(
                    statusMessage = "Subscribed"
                )
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e(TAG, "Subscribe failed", exception)
                uiState.value = uiState.value.copy(
                    statusMessage = "Subscribe failed"
                )
            }
        })
    }

    private fun unsubscribe() {
        mqttClient.unsubscribe(Config.topics.toTypedArray())
    }
}

@Composable
private fun MainContent(uiState: UiState) {
    Image(
        painter = painterResource(id = R.mipmap.office),
        contentDescription = "office",
        modifier = Modifier.fillMaxSize()
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.6f)
    ) {
        TopBar(statusMessage = uiState.statusMessage, messageCount = uiState.messageCount)
        Row(modifier = Modifier.weight(1f, true)) {
            Curtain(state = uiState.window1Open)
            Spacer(modifier = Modifier.width(2.dp))
            Curtain(state = uiState.window2Open)
            Spacer(modifier = Modifier.width(2.dp))
            Curtain(state = uiState.window3Open)
            Spacer(modifier = Modifier.width(2.dp))
            Curtain(state = uiState.window4Open)
        }
        BottomBar()
    }

    MessageOverlay(uiState)
}

@Composable
private fun TopBar(statusMessage: String, messageCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(20.dp, 10.dp)
    ) {
        Text(text = "STATUS: $statusMessage")
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "MSG COUNT: $messageCount")
    }
}

@Composable
private fun RowScope.Curtain(state: Boolean?) {
    val window4Color = when (state) {
        true -> Color.Red
        false -> Color.Green
        null -> Color.LightGray
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .background(color = window4Color)
            .weight(1f)
    ) {
    }
}

@Composable
private fun MessageOverlay(uiState: UiState) {
    Box(modifier = Modifier.fillMaxSize()) {
        val text: String
        val bgColor: Color
        if (!uiState.connected) {
            text = "MQTT not connected"
            bgColor = GrayTranslucent
        } else if (uiState.anyWindowIsOpen) {
            text = "Windows are open"
            bgColor = RedTranslucent
        } else if (uiState.incompleteSensorData) {
            text = "Incomplete sensor data"
            bgColor = GrayTranslucent
        } else {
            text = "Windows are closed.\nHave a nice day"
            bgColor = GreenTranslucent
        }

        Text(
            text = text,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 48.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .background(bgColor)
                .padding(40.dp)
        )
    }
}

@Composable
private fun BottomBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(20.dp, 10.dp)
    ) {
        Text(
            text = "DISCLAIMER: indicative only, data can be delayed by up to 3 minutes",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}


@Preview(showBackground = true, backgroundColor = 0xff000000, device = Devices.AUTOMOTIVE_1024p)
@Composable
fun MainContentPreviewOk() {
    MainContent(
        UiState(
            statusMessage = "subscribed",
            connected = true,
            messageCount = 1234,
            window1Open = false,
            window2Open = false,
            window3Open = false,
            window4Open = false
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xff000000, device = Devices.AUTOMOTIVE_1024p)
@Composable
fun MainContentPreviewNotConnected() {
    MainContent(
        UiState(
            statusMessage = "connection lost",
            connected = false,
            messageCount = 1234,
            window1Open = false,
            window2Open = false,
            window3Open = false,
            window4Open = false
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xff000000, device = Devices.AUTOMOTIVE_1024p)
@Composable
fun MainContentPreviewWindowOpen() {
    MainContent(
        UiState(
            statusMessage = "subscribed",
            connected = true,
            messageCount = 1234,
            window1Open = true,
            window2Open = false,
            window3Open = false,
            window4Open = false
        )
    )
}