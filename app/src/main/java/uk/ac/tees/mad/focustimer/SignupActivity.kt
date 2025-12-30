package uk.ac.tees.mad.focustimer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import uk.ac.tees.mad.focustimer.databinding.ActivitySignupBinding

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.signupButton.setOnClickListener {
            if (validateInput()) {
                createUser()
            }
        }

        binding.loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun createUser() {
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("SignupActivity", "createUserWithEmail:success")
                    val user = auth.currentUser
                    val userMap = hashMapOf(
                        "name" to binding.nameEditText.text.toString().trim(),
                        "email" to email,
                        "phone" to binding.phoneEditText.text.toString().trim()
                    )
                    db.collection("users").document(user!!.uid)
                        .set(userMap)
                        .addOnSuccessListener { 
                            Toast.makeText(baseContext, "Sign up successful.", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                         }
                        .addOnFailureListener { e -> 
                            Log.w("SignupActivity", "Error adding document", e)
                            Toast.makeText(baseContext, "Error saving user data.", Toast.LENGTH_SHORT).show()
                        }

                } else {
                    Log.w("SignupActivity", "createUserWithEmail:failure", task.exception)
                    if (task.exception is FirebaseAuthUserCollisionException) {
                        binding.emailInputLayout.error = "User with this email already exists"
                    } else {
                        Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun validateInput(): Boolean {
        val name = binding.nameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

        binding.nameInputLayout.error = null
        binding.emailInputLayout.error = null
        binding.phoneInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.confirmPasswordInputLayout.error = null

        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Name is required"
            return false
        }

        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Email is required"
            return false
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Invalid email format"
            return false
        }

        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = "Phone number is required"
            return false
        }

        if (!Patterns.PHONE.matcher(phone).matches()) {
            binding.phoneInputLayout.error = "Invalid phone number"
            return false
        }

        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Password is required"
            return false
        }

        if (password.length < 6) {
            binding.passwordInputLayout.error = "Password must be at least 6 characters long"
            return false
        }

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Please confirm your password"
            return false
        }

        if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = "Passwords do not match"
            return false
        }

        return true
    }
}