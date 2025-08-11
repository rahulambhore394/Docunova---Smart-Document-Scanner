package com.developer_rahul.docunova

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.developer_rahul.docunova.api.RetrofitClient
import com.developer_rahul.docunova.api.SignUpRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var fullNameET: EditText
    private lateinit var emailET: EditText
    private lateinit var passwordET: EditText
    private lateinit var confirmPasswordET: EditText
    private lateinit var registerBtn: Button
    private lateinit var haveAccountTV: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        fullNameET = findViewById(R.id.etFullName)
        emailET = findViewById(R.id.etEmail)
        passwordET = findViewById(R.id.etPassword)
        confirmPasswordET = findViewById(R.id.etConfirmPassword)
        registerBtn = findViewById(R.id.btnCreateAccount)
        haveAccountTV = findViewById(R.id.already_have)

        haveAccountTV.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        registerBtn.setOnClickListener {
            if (validateInputs()) {
                val email = emailET.text.toString().trim()
                val password = passwordET.text.toString()
                val fullName = fullNameET.text.toString().trim()
                signUp(email, password, fullName)
            }
        }
    }

    private fun validateInputs(): Boolean {
        val name = fullNameET.text.toString().trim()
        val email = emailET.text.toString().trim()
        val password = passwordET.text.toString()
        val confirm = confirmPasswordET.text.toString()

        return when {
            name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty() -> {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
                false
            }
            password.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                false
            }
            password != confirm -> {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun signUp(email: String, password: String, fullName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send fullName in user_metadata (data field)
                val response = RetrofitClient.instance.signUp(
                    SignUpRequest(
                        email = email,
                        password = password,
                        data = mapOf("full_name" to fullName)
                    )
                )

                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(
                            this@RegisterActivity,
                            "Sign-up successful! Please check your email to confirm.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    }
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    runOnUiThread {
                        Toast.makeText(this@RegisterActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@RegisterActivity, "Exception: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
