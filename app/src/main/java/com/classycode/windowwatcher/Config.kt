package com.classycode.windowwatcher

class Config {

    companion object {

        // these values are obtained from the MQTT integration in the Things Network Console
        const val brokerUrl = "tcp://eu1.cloud.thethings.network:1883"
        const val brokerUsername = "<your-app-id>e@ttn"
        const val brokerPassword = "<your api key>"

        const val clientId = "<your client id" // doesn't matter

        // the EMS sensors
        val devices = listOf(
            // a list of device ids
        )

        // the uplink topics
        val topics: List<String> =
            devices.map { deviceName -> "v3/${brokerUsername}/devices/${deviceName}/up" }
    }
}