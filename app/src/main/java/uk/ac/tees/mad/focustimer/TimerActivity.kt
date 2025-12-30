package uk.ac.tees.mad.focustimer
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import org.json.JSONArray
import uk.ac.tees.mad.focustimer.databinding.ActivityTimerBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

class TimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimerBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var timer: CountDownTimer? = null
    private var timerRunning = false
    private var timeLeftInMillis: Long = 0
    private var focusLengthInMillis: Long = 0

    private var isFocusSession = true
    private var focusSessionsCompletedToday = 0
    private var currentQuoteText = "Stay focused and keep moving forward!"
    private var pendingCompletionMessage: String? = null
    private var isGoalAchievementShown = false

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        createNotificationChannel()
        requestNotificationPermission()
        loadStats()
        
        binding.quoteCard.visibility = View.GONE

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.statsButton.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        binding.startPauseButton.setOnClickListener {
            if (timerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        binding.resetButton.setOnClickListener {
            resetTimer()
        }
    }

    override fun onResume() {
        super.onResume()
        handleIntent(intent)
        loadStats()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.hasExtra("startBreak")) {
            isFocusSession = false
            loadTimerSettings()
            resetTimer()
            startTimer()
            intent.removeExtra("startBreak") 
        } else if (intent.hasExtra("startFocus")) {
            isFocusSession = true
            loadTimerSettings()
            resetTimer()
            startTimer()
            intent.removeExtra("startFocus") 
        } else {
            loadTimerSettings()
            updateTimerText()
        }
    }

    private fun loadTimerSettings() {
        val sharedPreferences = getSharedPreferences("TimerSettings", Context.MODE_PRIVATE)
        val focusLength = sharedPreferences.getLong("focusLength", 25)
        focusLengthInMillis = focusLength * 60 * 1000
        val shortBreakLength = sharedPreferences.getLong("shortBreakLength", 5)
        val longBreakLength = sharedPreferences.getLong("longBreakLength", 15)

        if (!timerRunning) {
            timeLeftInMillis = if (isFocusSession) {
                focusLengthInMillis
            } else {
                if ((focusSessionsCompletedToday % 4) == 0 && focusSessionsCompletedToday != 0) {
                    longBreakLength * 60 * 1000
                } else {
                    shortBreakLength * 60 * 1000
                }
            }
        }
        
        binding.sessionStatusChip.text = if (isFocusSession) "FOCUS" else "BREAK"
    }

    private fun startTimer() {
        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
            }

            override fun onFinish() {
                timerRunning = false
                updateButtonUI()
                
                if (isFocusSession) {
                    updateStats(focusLengthInMillis)
                    isFocusSession = false
                    showBreakNotification()
                    pendingCompletionMessage = "Great job! Focus session completed."
                } else {
                    isFocusSession = true
                    showFocusNotification()
                    pendingCompletionMessage = "Break ended. Time to focus!"
                }
                
                FetchQuoteTask().execute()
                loadTimerSettings()
                updateTimerText()
            }
        }.start()

        timerRunning = true
        updateButtonUI()
    }

    private fun updateGoalProgress() {
        val sharedPreferences = getSharedPreferences("TimerSettings", Context.MODE_PRIVATE)
        val dailyGoal = sharedPreferences.getInt("dailyGoal", 4)
        
        binding.goalProgressText.text = "$focusSessionsCompletedToday/$dailyGoal"
        binding.goalProgressBar.max = dailyGoal
        binding.goalProgressBar.progress = focusSessionsCompletedToday

        // Check if goal is reached and cheer the user
        if (focusSessionsCompletedToday >= dailyGoal && !isGoalAchievementShown) {
            isGoalAchievementShown = true
            showGoalReachedDialog()
        } else if (focusSessionsCompletedToday < dailyGoal) {
            isGoalAchievementShown = false // Reset if goal is increased
        }
    }

    private fun showGoalReachedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Daily Goal Achieved! 🎉")
            .setMessage("Fantastic work! You've reached your focus goal for today. Consistency is key to success—keep it up!")
            .setPositiveButton("Awesome!") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showCompletionPopup(message: String, quote: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(message)
            .setMessage(quote)
            .setPositiveButton("Continue") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun pauseTimer() {
        timer?.cancel()
        timerRunning = false
        updateButtonUI()
    }

    private fun resetTimer() {
        timer?.cancel()
        timerRunning = false
        loadTimerSettings()
        updateTimerText()
        updateButtonUI()
        binding.progressIndicator.progress = 100
    }

    private fun updateButtonUI() {
        if (timerRunning) {
            binding.startPauseButton.text = "Pause"
            binding.startPauseButton.icon = ContextCompat.getDrawable(this, android.R.drawable.ic_media_pause)
        } else {
            binding.startPauseButton.text = if (isFocusSession) "Start Focus" else "Start Break"
            binding.startPauseButton.icon = ContextCompat.getDrawable(this, android.R.drawable.ic_media_play)
        }
    }

    private fun updateTimerText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)
        binding.timerTextView.text = timeFormatted

        val sharedPreferences = getSharedPreferences("TimerSettings", Context.MODE_PRIVATE)
        val totalTime = if (isFocusSession) {
            sharedPreferences.getLong("focusLength", 25) * 60 * 1000
        } else {
            if ((focusSessionsCompletedToday % 4) == 0 && focusSessionsCompletedToday != 0) {
                sharedPreferences.getLong("longBreakLength", 15) * 60 * 1000
            } else {
                sharedPreferences.getLong("shortBreakLength", 5) * 60 * 1000
            }
        }
        if (totalTime > 0) {
            binding.progressIndicator.progress = (timeLeftInMillis * 100 / totalTime).toInt()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "FocusTimer Channel"
            val descriptionText = "Channel for FocusTimer notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("FOCUS_TIMER_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showBreakNotification() {
        val intent = Intent(this, TimerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("startBreak", true)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val sharedPreferences = getSharedPreferences("TimerSettings", Context.MODE_PRIVATE)
        val breakLength = if ((focusSessionsCompletedToday % 4) == 0 && focusSessionsCompletedToday != 0) {
            sharedPreferences.getLong("longBreakLength", 15)
        } else {
            sharedPreferences.getLong("shortBreakLength", 5)
        }

        val builder = NotificationCompat.Builder(this, "FOCUS_TIMER_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Focus Session Complete!")
            .setContentText("Time for a ${breakLength}-minute break.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            with(NotificationManagerCompat.from(this)) {
                notify(1, builder.build())
            }
        }
    }

    private fun showFocusNotification() {
        val intent = Intent(this, TimerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("startFocus", true)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, "FOCUS_TIMER_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Break's Over!")
            .setContentText("Time to get back to focus.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            with(NotificationManagerCompat.from(this)) {
                notify(2, builder.build())
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateStats(focusTime: Long) {
        val user = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(user.uid)
        
        userDocRef.update("totalFocusTime", FieldValue.increment(focusTime / 1000))
        userDocRef.update("sessionsCompleted", FieldValue.increment(1))
            .addOnSuccessListener {
                loadStats()
            }

        val session = hashMapOf(
            "type" to "Focus",
            "duration" to focusTime / 1000,
            "timestamp" to Timestamp.now()
        )
        userDocRef.collection("sessions").add(session)
    }

    private fun loadStats() {
        val user = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(user.uid)
        
        // Load today's stats specifically
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = Timestamp(calendar.time)

        userDocRef.collection("sessions")
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .get()
            .addOnSuccessListener { documents ->
                focusSessionsCompletedToday = documents.size()
                binding.sessionsCompletedValue.text = focusSessionsCompletedToday.toString()
                
                // Calculate today's focus time
                var todaySeconds: Long = 0
                for (doc in documents) {
                    todaySeconds += doc.getLong("duration") ?: 0
                }
                val hours = todaySeconds / 3600
                val minutes = (todaySeconds % 3600) / 60
                binding.totalFocusTimeValue.text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                
                updateGoalProgress()
            }
    }

    private inner class FetchQuoteTask : AsyncTask<Void, Void, String>() {
        override fun doInBackground(vararg params: Void?): String? {
            var urlConnection: HttpURLConnection? = null
            try {
                val url = URL("https://zenquotes.io/api/random")
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.connectTimeout = 5000
                urlConnection.readTimeout = 5000
                val reader = BufferedReader(InputStreamReader(urlConnection.inputStream))
                val stringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                }
                return stringBuilder.toString()
            } catch (e: Exception) {
                Log.e("TimerActivity", "Error fetching quote: ${e.message}")
            } finally {
                urlConnection?.disconnect()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            var finalQuote = currentQuoteText
            if (result != null) {
                try {
                    val jsonArray = JSONArray(result)
                    val jsonObject = jsonArray.getJSONObject(0)
                    val quote = jsonObject.getString("q")
                    val author = jsonObject.getString("a")
                    finalQuote = "\"$quote\" — $author"
                } catch (e: Exception) {
                    Log.e("TimerActivity", "Error parsing quote: ${e.message}")
                }
            }
            
            pendingCompletionMessage?.let {
                showCompletionPopup(it, finalQuote)
                pendingCompletionMessage = null
            }
        }
    }
}