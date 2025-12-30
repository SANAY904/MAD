package uk.ac.tees.mad.focustimer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale
import uk.ac.tees.mad.focustimer.databinding.ActivityStatsBinding

data class Session(
    val type: String = "",
    val timestamp: com.google.firebase.Timestamp? = null,
    val duration: Long = 0 // in seconds
)

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        loadStats()
    }

    private fun loadStats() {
        val user = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(user.uid)

        userDocRef.collection("sessions").orderBy("timestamp", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { documents ->
                val sessions = documents.toObjects(Session::class.java)
                
                if (sessions.isEmpty()) {
                    binding.emptyStateView.visibility = View.VISIBLE
                    binding.recentActivityRecyclerView.visibility = View.GONE
                    updateSummary(0, 0)
                } else {
                    binding.emptyStateView.visibility = View.GONE
                    binding.recentActivityRecyclerView.visibility = View.VISIBLE
                    
                    val totalDurationSeconds = sessions.sumOf { it.duration }
                    updateSummary(totalDurationSeconds, sessions.size)
                    
                    val adapter = SessionAdapter(sessions)
                    binding.recentActivityRecyclerView.adapter = adapter
                    binding.recentActivityRecyclerView.layoutManager = LinearLayoutManager(this)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load stats: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.emptyStateView.visibility = View.VISIBLE
            }
    }

    private fun updateSummary(totalSeconds: Long, count: Int) {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        binding.totalTimeText.text = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        binding.sessionCountText.text = count.toString()
        val avgMinutes = if (count > 0) (totalSeconds / count) / 60 else 0
        binding.avgSessionText.text = "${avgMinutes}m"
    }
}

class SessionAdapter(private val sessions: List<Session>) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sessionType: TextView = view.findViewById(R.id.sessionType)
        val sessionDate: TextView = view.findViewById(R.id.sessionDate)
        val sessionDuration: TextView = view.findViewById(R.id.sessionDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.sessionType.text = session.type.replaceFirstChar { it.uppercase() }
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.sessionDate.text = if (session.timestamp != null) sdf.format(session.timestamp.toDate()) else ""
        holder.sessionDuration.text = "${session.duration / 60} min"
    }

    override fun getItemCount() = sessions.size
}