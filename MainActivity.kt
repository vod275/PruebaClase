package com.example.aplicacionalarcos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.aplicacionalarcos.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Configure One Tap for Google Sign-In
        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        // Configure standard Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // ... (your existing click listeners for email/password login and registration)

        binding.googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.editText?.text.toString().trim()
            val password = binding.passwordInput.editText?.text.toString().trim()


            if (email.isNotEmpty() && password.isNotEmpty()) {
                if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    // Si el correo es válido, proceder con el inicio de sesión
                    signIn(email, password)
                } else {
                    // Mostrar un mensaje si el correo no tiene el formato correcto
                    Toast.makeText(this, "Por favor, ingresa un correo válido", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Si algún campo está vacío
                Toast.makeText(this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
            }
        }


        binding.registerButton.setOnClickListener {
            val email = binding.emailInput.editText?.text.toString().trim()
            val password = binding.passwordInput.editText?.text.toString().trim()
            // Configure standard Google Sign-In
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)

            if (email.isNotEmpty() && password.isNotEmpty()) {
                register(email, password)
            } else {
                Toast.makeText(this, "Por favor, llena todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    Log.w("Login", "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        this,
                        "Error: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateUI(null)
                }
            }
    }

    private fun register(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Toast.makeText(this, "Usuario registrado exitosamente", Toast.LENGTH_SHORT).show()
                    updateUI(user)
                } else {
                    // Aquí obtenemos el mensaje completo del error
                    val errorMessage = task.exception?.message ?: "Error desconocido. Intenta nuevamente."

                    Log.w("Register", "createUserWithEmail:failure", task.exception)

                    // Mostrar un mensaje dependiendo del error
                    when {
                        errorMessage.contains("email address is already in use") -> {
                            Toast.makeText(this, "El correo ya está registrado", Toast.LENGTH_SHORT).show()
                        }
                        errorMessage.contains("The given password is invalid") -> {
                            Toast.makeText(this, "La contraseña es demasiado débil", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // Mostrar el error, si no se puede identificar el tipo específico
                            Toast.makeText(this, "Error al registrar: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
                    updateUI(null)
                }
            }
    }



    private fun signInWithGoogle() {
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    signInLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent).build())
                } catch (e: android.content.IntentSender.SendIntentException) {
                    Log.e("GoogleSignIn", "Couldn't start One Tap UI: ${e.localizedMessage}")
                    // Fallback to standard Google Sign-In if One Tap UI fails to start
                    val signInIntent = googleSignInClient.signInIntent
                    startActivityForResult(signInIntent, RC_SIGN_IN)
                }
            }
            .addOnFailureListener { e ->
                Log.w("GoogleSignIn", "One Tap Sign-In failed", e)
                // Fallback to standard Google Sign-In
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, RC_SIGN_IN)
            }
    }

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                    val idToken = credential.googleIdToken
                    if (idToken != null) {
                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                        auth.signInWithCredential(firebaseCredential)
                            .addOnCompleteListener(this) { task ->
                                if (task.isSuccessful) {
                                    val user = auth.currentUser
                                    updateUI(user)
                                } else {
                                    Log.w(
                                        "GoogleSignIn",
                                        "signInWithCredential:failure",
                                        task.exception
                                    )
                                    Toast.makeText(
                                        this,
                                        "Error al autenticar con Google",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                } catch (e: ApiException) {
                    // Google Sign In failed, update UI appropriately
                    Log.w("GoogleSignIn", "Google sign in failed", e)
                    Toast.makeText(this, "Error al iniciar sesión con Google", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            user.reload().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val refreshedUser = auth.currentUser
                    if (refreshedUser != null) {
                        Toast.makeText(
                            this,
                            "Bienvenido: ${refreshedUser.email}",
                            Toast.LENGTH_SHORT
                        ).show()
                        val intent = Intent(this, DatosUsuarioActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "Usuario no encontrado, inicia sesión nuevamente.",
                            Toast.LENGTH_SHORT
                        ).show()
                        auth.signOut()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Error al verificar usuario: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    auth.signOut()
                }
            }
        } else {
            Toast.makeText(this, "Por favor, inicia sesión o regístrate", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }
}