package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import org.json.JSONObject
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val HOST = "broker.hivemq.com"
    private val BASE = "inacap/seijakuh323/cortinas"

    private lateinit var client: Mqtt3AsyncClient
    private lateinit var tvConexion: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvLuz: TextView
    private lateinit var tvEstado: TextView
    private val prefs by lazy { getSharedPreferences("cortinas", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val correo = intent.getStringExtra("correo")
        findViewById<TextView>(R.id.tvBienvenida).text = "Bienvenido\n$correo"
        tvConexion = findViewById(R.id.tvConexion)
        tvTemp = findViewById(R.id.tvTemp)
        tvLuz = findViewById(R.id.tvLuz)
        tvEstado = findViewById(R.id.tvEstado)

        // Almacenamiento local: muestra el último dato guardado mientras llega uno nuevo
        tvTemp.text = "Temperatura: ${prefs.getString("temp", "--")} °C (último guardado)"
        tvLuz.text = "Luz: ${prefs.getString("luz", "--")}"
        tvEstado.text = "Cortina: ${prefs.getString("estado", "--")}"

        findViewById<Button>(R.id.btnAbrir).setOnClickListener { enviar("ABRIR") }
        findViewById<Button>(R.id.btnCerrar).setOnClickListener { enviar("CERRAR") }
        findViewById<Button>(R.id.btnAuto).setOnClickListener { enviar("AUTO") }
        findViewById<Button>(R.id.btnSalir).setOnClickListener {
            startActivity(Intent(this, loginactivity::class.java))
            finish()
        }

        conectar()
    }

    private fun conectar() {
        client = Mqtt3Client.builder()
            .identifier("android-" + UUID.randomUUID())
            .serverHost(HOST)
            .serverPort(8883)
            .sslWithDefaultConfig()               // conexión cifrada (TLS)
            .automaticReconnectWithDefaultConfig() // reconexión automática
            .addConnectedListener {
                runOnUiThread { tvConexion.text = "Conectado (TLS)" }
                suscribir()
            }
            .addDisconnectedListener {
                runOnUiThread { tvConexion.text = "Desconectado, reconectando..." }
            }
            .buildAsync()
        intentarConectar()
    }

    private fun intentarConectar() {
        client.connectWith().cleanSession(true).send().whenComplete { _, err ->
            if (err != null && !isDestroyed) {
                runOnUiThread { tvConexion.text = "Sin conexión, reintentando..." }
                Handler(Looper.getMainLooper()).postDelayed({ if (!isDestroyed) intentarConectar() }, 5000)
            }
        }
    }

    private fun suscribir() {
        client.subscribeWith().topicFilter("$BASE/datos").qos(MqttQos.AT_MOST_ONCE)
            .callback { p -> val m = String(p.payloadAsBytes); runOnUiThread { mostrarDatos(m) } }
            .send()
        client.subscribeWith().topicFilter("$BASE/estado").qos(MqttQos.AT_LEAST_ONCE)
            .callback { p -> val m = String(p.payloadAsBytes); runOnUiThread { mostrarEstado(m) } }
            .send()
    }

    private fun mostrarDatos(json: String) {
        try {
            val j = JSONObject(json)
            val temp = j.optDouble("temp").toString()
            val luz = j.optInt("luz").toString()
            tvTemp.text = "Temperatura: $temp °C"
            tvLuz.text = "Luz: $luz"
            prefs.edit().putString("temp", temp).putString("luz", luz).apply()
        } catch (_: Exception) { }
    }

    private fun mostrarEstado(json: String) {
        try {
            val j = JSONObject(json)
            val txt = "${j.optString("cortina")} (${j.optString("modo")})"
            tvEstado.text = "Cortina: $txt"
            prefs.edit().putString("estado", txt).apply()
        } catch (_: Exception) { }
    }

    private fun enviar(cmd: String) {
        if (!client.state.isConnected) {
            Toast.makeText(this, "Sin conexión con el broker", Toast.LENGTH_SHORT).show()
            return
        }
        client.publishWith().topic("$BASE/cmd").qos(MqttQos.AT_LEAST_ONCE)
            .payload(cmd.toByteArray()).send()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::client.isInitialized) client.disconnect()
    }
}