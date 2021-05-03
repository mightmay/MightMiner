package might.miner

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit



class MainActivity : AppCompatActivity(){
    private var svc: ScheduledExecutorService? = null
    private var tvLog: TextView? = null
    private var edPool: EditText? = null
    private var edUser: EditText? = null
    private var edThreads: EditText? = null
    private var edMaxCpu: EditText? = null
    private var tvSpeed: TextView? = null
    private var tvAccepted: TextView? = null
    private var cbUseWorkerId: CheckBox? = null
    private var validArchitecture = true

    private var binder: MiningService.MiningServiceBinder? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //enableButtons(false)

        // wire views
        tvLog = findViewById(R.id.output)
        tvSpeed = findViewById(R.id.speed)
        tvAccepted = findViewById(R.id.accepted)
        edPool = findViewById(R.id.pool)
        edUser = findViewById(R.id.username)
        edThreads = findViewById(R.id.threads)
        edMaxCpu = findViewById(R.id.maxcpu)
        cbUseWorkerId = findViewById(R.id.use_worker_id)
        var titleText: TextView?  = findViewById(R.id.textView)

        // check architecture
        if (!Arrays.asList(*SUPPORTED_ARCHITECTURES).contains(Build.CPU_ABI.toLowerCase())) {
            Toast.makeText(this, "Sorry, this app currently only supports 64 bit architectures, but yours is " + Build.CPU_ABI, Toast.LENGTH_LONG).show()
            // this flag will keep the start button disabled
            //validArchitecture = false
            if (titleText != null) {
                titleText.setText("CPU NOT SUPPORTED")
            }
        }

        // run the service
        val intent = Intent(this, MiningService::class.java)
        bindService(intent, serverConnection, BIND_AUTO_CREATE)
        startService(intent)
    }

    fun startMining(view: View) {
        println("TRYING TO START")
        if (binder == null) {
            println("BINDER IS NULL")

            return
        }
        val cfg: MiningService.MiningConfig = binder!!.getService().newConfig(edUser!!.text.toString(), edPool!!.text.toString(), edThreads!!.text.toString().toInt(), edMaxCpu!!.text.toString().toInt(), cbUseWorkerId!!.isChecked)
        binder!!.getService().startMining(cfg)
    }

    fun stopMining(view: View) {
        this.binder?.getService()?.stopMining()
    }

    override fun onResume() {
        super.onResume()
        // the executor which will load and display the service status regularly
        svc = Executors.newSingleThreadScheduledExecutor()
        svc?.run { scheduleWithFixedDelay(Runnable { updateLog() }, 1, 1, TimeUnit.SECONDS) }
    }

    override fun onPause() {
        svc!!.shutdown()
        super.onPause()
    }

    private val serverConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            println("onServiceConnected")

            binder = iBinder as MiningService.MiningServiceBinder
            if (validArchitecture) {
                enableButtons(true)
                findViewById<View>(R.id.start).setOnClickListener { view: View -> startMining(view) }
                findViewById<View>(R.id.stop).setOnClickListener { view: View -> stopMining(view) }
                val cores: Int = binder!!.getService().getAvailableCores()
                // write suggested cores usage into editText
                var suggested = cores / 2
                if (suggested == 0) suggested = 1
                edThreads!!.text.clear()
                edThreads!!.text.append(Integer.toString(suggested))
                (findViewById<View>(R.id.cpus) as TextView).text = String.format("(%d %s)", cores, getString(R.string.cpus))
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            println("onServiceDisconnected")

            binder = null
            //enableButtons(false)
        }
    }

    private fun enableButtons(enabled: Boolean) {
        findViewById<View>(R.id.start).isEnabled = enabled
        findViewById<View>(R.id.stop).isEnabled = enabled
    }

    private fun updateLog() {
        runOnUiThread {
            if (binder != null) {
                tvLog?.setText(binder!!.getService().getOutput())
                tvAccepted!!.text = Integer.toString(binder!!.getService().getAccepted())
                tvSpeed?.setText(binder!!.getService().getSpeed())
            }
        }
    }

    companion object {
        private val SUPPORTED_ARCHITECTURES = arrayOf("arm64-v8a", "armeabi-v7a")
    }
}
