package com.example.ppgsensor.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.ktx.storage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*



class MainActivity : ComponentActivity(), SensorEventListener {
    //センササービスへの参照を取得するためにインスタンスを作成
    private lateinit var sensorManager: SensorManager
    private var ppgSensor: Sensor? = null
    private var accSensor: Sensor? = null

    //PPGセンサから得られた値
    private var ppgValue by mutableStateOf(0f)
    private var accValueX by mutableStateOf(0f)
    private var accValueY by mutableStateOf(0f)
    private var accValueZ by mutableStateOf(0f)
    //センサリスナに登録しているかどうかのフラグ
    private var isListening by mutableStateOf(false)
    //記録されているかどうかのフラグ
    private  var isRecording by mutableStateOf(false)
    private val ppgValueList = mutableListOf<String>()
    private val accValueList = mutableListOf<String>()

    //外部ストレージに保存するパーミッション
    private val WRITE_EXTERNAL_STORAGE_REQUEST = 1

    //firebase cloud storageに保存
    val storage = Firebase.storage

    private lateinit var auth: FirebaseAuth


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        //アプリ内でFirebaseを初期化
        FirebaseApp.initializeApp(this)

        auth = FirebaseAuth.getInstance()

        // 匿名サインインを実行
//        signInAnonymously()
        // 新しいアカウントを作成またはログイン

        auth.signInWithEmailAndPassword("example@example.com", "password123")
            .addOnCompleteListener(this) { task ->
                Log.d("Create", "Create")
                if (task.isSuccessful) {
                    // アカウント作成成功時の処理
                    val user: FirebaseUser? = auth.currentUser
                    Log.d("TAG", "アカウント作成成功 UID: ${user?.uid}")
                } else {
                    // エラー時の処理
                    Log.e("TAG", "アカウント作成エラー", task.exception)
                }
            }


        if(sensorManager.getDefaultSensor(65572).type == 65572) {
            setContent {
                MaterialTheme {
                    PPGScreen(ppgValue.toString(), accValueX.toString(), accValueY.toString(), accValueZ.toString(), isListening, onToggleListening)
                }
            }
        }

        ppgSensor = sensorManager.getDefaultSensor(65572)
        Log.d("TAG", ppgSensor.toString())

        // 外部ストレージへの書き込み許可をリクエスト
        requestStoragePermission()


        // 必要なパーミッションのリクエスト
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                PERMISSION_REQUEST_BODY_SENSORS
            )
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                WRITE_EXTERNAL_STORAGE_REQUEST
            )
        } else {
            setupSensor()
        }
    }


    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
//            reload()
        }
    }

//    private fun signInAnonymously() {
//        auth.signInAnonymously()
//            .addOnCompleteListener(this) { task ->
//                if (task.isSuccessful) {
//                    // サインイン成功時の処理
//                    val user: FirebaseUser? = auth.currentUser
//                    Log.d("TAG", "匿名サインイン成功 UID: ${user?.uid}")
//                } else {
//                    // エラー時の処理
//                    Log.e("TAG", "匿名サインインエラー", task.exception)
//                }
//            }
//    }
    private fun requestStoragePermission() {
        Log.d("storageRequest", "StorageRequest")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
        }
    }


    //ユーザがセンサの使用を許可したときに発火する
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_BODY_SENSORS) {
            if (grantResults.isNotEmpty() && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                setupSensor()
            } else {
                // パーミッションが拒否された場合の処
            }
        }
    }

    private fun setupSensor() {
        sensorManager.registerListener(
            this,
            ppgSensor,
            SensorManager.SENSOR_DELAY_GAME
        )
        sensorManager.registerListener(
            this,
            accSensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    //onResume関数はActivityが表示されたときに実行されるライフサイクルフック
    //センサのリスナの登録はここで行う
    override fun onResume() {
        super.onResume()
        // setupSensor()を呼んで、センサをセンサリスナに登録
//        setupSensor()

        ppgSensor = sensorManager.getDefaultSensor(65572) // PPGセンサを用いる
        sensorManager.registerListener(this, ppgSensor, SensorManager.SENSOR_DELAY_FASTEST) // 設定したセンサに対してイベントリスナを設定
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // センサーの精度が変更された場合の処理
    }

    //センサから情報が得られたときに実行される
    override fun onSensorChanged(event: SensorEvent?) {

//        val currentTimeMillis = System.currentTimeMillis()
//        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
//        val timestamp = sdf.format(currentTimeMillis)
        val dateFormat = SimpleDateFormat("HH-mm-ss-SSS")
        val currentDateTime = Date()
        val timestamp = dateFormat.format(currentDateTime)
        //.?はセーフコール演算子、nullでなければ、処理が実行される. eventオブジェクトにセンサイベントが入ってる。
        event?.let {
            if(it.sensor.type == 65572) {
                ppgValue = it.values[0]
                if(isRecording) {
                    ppgValueList.add("$timestamp, $ppgValue")
                }
            }
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                accValueX = it.values[0]
                accValueY = it.values[1]
                accValueZ = it.values[2]
                accValueList.add("$timestamp, $accValueX, $accValueY, $accValueZ")
            }
        }
    }

    private fun saveDataToFile() {
        val directoryPath: String = "/storage/emulated/0/Documents"
        val filename1 = "acc_data1.csv"
        val filePath1 = File(directoryPath, filename1)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
        val currentDateTime = Date()
        val timestamp = dateFormat.format(currentDateTime)

        try {
            // CSVファイルにデータを書き込む
            val csvWriter = FileWriter(filePath1)
            for (line in accValueList) {
                csvWriter.append(line)
                csvWriter.append("\n")
            }
            csvWriter.flush()
            csvWriter.close()
            println("加速度センサーデータをCSVファイルに保存しました。")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val filename2 = "ppg_data1.csv"
        val filePath2 = File(directoryPath, filename2)

        try {
            // CSVファイルにデータを書き込む
            val csvWriter = FileWriter(filePath2)
            for (line in ppgValueList) {
                csvWriter.append(line)
                csvWriter.append("\n")
            }
            csvWriter.flush()
            csvWriter.close()
            println("ppgセンサーデータをCSVファイルに保存しました。")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val storageRef = storage.reference
        var accDataRef: StorageReference? = storageRef.child("$timestamp+acc.csv")
        val accCsvFile = Uri.fromFile(File("/storage/emulated/0/Documents/acc_data1.csv"))
        val uploadTask = accDataRef?.putFile(accCsvFile)

        val user = auth.currentUser
        Log.d("user", "$user")
        uploadTask?.addOnSuccessListener {
            Log.d("FirebaseStorage", "CSVファイルが正常にアップロードされました。")
        }?.addOnFailureListener { e ->
            Log.e("FirebaseStorage", "CSVファイルのアップロード中にエラーが発生しました。", e)
        }

        var ppgDataRef: StorageReference? = storageRef.child("$timestamp+ppg.csv")
        val ppgCsvFile = Uri.fromFile(File("/storage/emulated/0/Documents/ppg_data1.csv"))
        ppgDataRef?.putFile(ppgCsvFile)
    }

    override fun onStop() {
        super.onStop()
        Log.d("stop", "stop")
    }
    private val onToggleListening: () -> Unit = {
        if(isRecording){
            Log.d("stop", "stop")
            stopRecording()
        }else{
            Log.d("start", "start")
        }
        isListening = !isListening
        isRecording = !isRecording
    }

    private fun stopRecording() {
//        isRecording = false
//        sensorManager.unregisterListener(this)
        saveDataToFile()
        //リストの値をすべて削除する
//        ppgValueList.clear()
    }


    companion object {
        private const val PERMISSION_REQUEST_BODY_SENSORS = 1001
    }
}

@Composable
fun PPGScreen(ppgValue: String, accValueX:String, accValueY:String, accValueZ:String, isListening: Boolean, onToggleListening: () -> Unit) {
    val padding = 16.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            text = ppgValue
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            text = accValueX
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            text = accValueY
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.primary,
            text = accValueZ
        )
        Spacer(modifier = Modifier.size(padding))
        Button(

            onClick = { onToggleListening() }, // クリックされたら、isListeningのtrue,false切り替え
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if(isListening) Color.Red else Color.Green
            )

        ) {
            Text(text = if (isListening) "Stop" else "Start")
        }

    }
}


@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    PPGScreen("Preview Android", "null", "null", "null", isListening = true, onToggleListening = {})
}

