package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class loginactivity : AppCompatActivity() {

    // IP de la Raspbian + carpeta de tu proyecto
    private val BASE_URL = "http://192.168.1.18/cortinas"

    // TEMPORAL: true = entra sin llamar a la VM (solo para probar MQTT). Poner en false para el login real.
    private val MODO_PRUEBA = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loginactivity)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPass = findViewById<EditText>(R.id.etPass)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val e = etEmail.text.toString().trim()
            val p = etPass.text.toString()
            if (MODO_PRUEBA) { irAMain(if (e.isEmpty()) "prueba" else e); return@setOnClickListener }
            if (e.isEmpty() || p.isEmpty()) { toast("Completa los campos"); return@setOnClickListener }
            llamarApi("login.php", e, p)
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val e = etEmail.text.toString().trim()
            val p = etPass.text.toString()
            if (e.isEmpty() || p.length < 6) { toast("Correo y contraseña (mín. 6)"); return@setOnClickListener }
            llamarApi("registro.php", e, p)
        }
    }

    private fun llamarApi(script: String, correo: String, pass: String) {
        Thread {
            try {
                val con = URL("$BASE_URL/$script").openConnection() as HttpURLConnection
                con.requestMethod = "POST"
                con.connectTimeout = 5000
                con.readTimeout = 5000
                con.doOutput = true
                val body = "correo=" + URLEncoder.encode(correo, "UTF-8") +
                        "&password=" + URLEncoder.encode(pass, "UTF-8")
                con.outputStream.use { it.write(body.toByteArray()) }
                val texto = con.inputStream.bufferedReader().readText().trim()
                runOnUiThread { if (texto == "OK") irAMain(correo) else toast(texto) }
            } catch (ex: Exception) {
                runOnUiThread { toast("Sin conexión: ${ex.message}") }
            }
        }.start()
    }

    private fun irAMain(correo: String) {
        val i = Intent(this, MainActivity::class.java)
        i.putExtra("correo", correo)
        startActivity(i)
        finish()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}