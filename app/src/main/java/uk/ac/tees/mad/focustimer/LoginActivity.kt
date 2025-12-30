package uk.ac.tees.mad.focustimer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.google.firebase.auth.FirebaseAuth
import uk.ac.tees.mad.focustimer.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        // If user is already logged in, skip this activity entirely
        if (auth.currentUser != null) {
            startActivity(Intent(this, TimerActivity::class.java))
            finish()
            return
        }

        setTheme(R.style.Theme_FocusTimer) // Switch back to the main theme
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Clear errors when user starts typing
        binding.emailEditText.doOnTextChanged { _, _, _, _ ->
            binding.emailInputLayout.error = null
        }
        binding.passwordEditText.doOnTextChanged { _, _, _, _ ->
            binding.passwordInputLayout.error = null
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            // Reset errors
            binding.emailInputLayout.error = null
            binding.passwordInputLayout.error = null

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(baseContext, "Login successful.", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, TimerActivity::class.java))
                            finish()
                        } else {
                            // If sign in fails, display a message to the user.
                            binding.emailInputLayout.error = "Invalid email or password"
                            binding.passwordInputLayout.error = "Invalid email or password"
                        }
                    }
            } else {
                if (email.isEmpty()) binding.emailInputLayout.error = "Email is required"
                if (password.isEmpty()) binding.passwordInputLayout.error = "Password is required"
            }
        }

        binding.signupTextView.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }
}