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
    private val ppgValueList = mutableListOf<Float>()
    private val accValueList = mutableListOf<Float>()

    //firebase cloud storageに保存
    val storage = Firebase.storage
    val storageRef = storage.reference
    var dataRef: StorageReference? = storageRef.child("data")




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        //アプリ内でFirebaseを初期化
        FirebaseApp.initializeApp(this)


        //全てのセンサを取得する
//        val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
//        for (currentSensor in deviceSensors) {
//            Log.d("List sensors", "Name: ${currentSensor.name} /Type_String: ${currentSensor.stringType} /Type_number: ${currentSensor.type}")
//        }


        if(sensorManager.getDefaultSensor(65572).type == 65572) {
            setContent {
                MaterialTheme {
                    PPGScreen(ppgValue.toString(), accValueX.toString(), accValueY.toString(), accValueZ.toString(), isListening, onToggleListening)
                }
            }
        }

        ppgSensor = sensorManager.getDefaultSensor(65572)
        Log.d("TAG", ppgSensor.toString())

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
        } else {
            setupSensor()
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
                // パーミッションが拒否された場合の処理
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
        //リストの値をすべて削除する
        ppgValueList.clear()
        ppgSensor = sensorManager.getDefaultSensor(65572) // PPGセンサを用いる
        sensorManager.registerListener(this, ppgSensor, SensorManager.SENSOR_DELAY_NORMAL) // 設定したセンサに対してイベントリスナを設定
//        Log.d("FASTEST", "${SensorManager.SENSOR_DELAY_FASTEST}")
//        Log.d("GAME", "${SensorManager.SENSOR_DELAY_GAME}")
//        Log.d("NORMAL", "${SensorManager.SENSOR_DELAY_NORMAL}")
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        Log.d("ppgValueList", "$ppgValueList")

        val ppgFileName = "ppg_data1.csv"
        val accFileName = "acc_data.csv"
//        val filepath: String = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).toString()
        // /storage/emulated/0/Android/data/com.example.ppgsensor/files/Documents./Log.csv
        var filepath = Environment.getExternalStorageDirectory().toString()
        Log.d("filepath", "$filepath")
        try {
            // 内部ストレージにファイルを作成
            Log.d("Before", "before")
            val ppgfile = File(filepath, ppgFileName)
            val accfile = File(filepath, accFileName)
            Log.d("after", "after")
            // this.filesDir = /data/data/com.example.ppgsensor/files
            val directory = this.filesDir
            val fileWriter = FileWriter(ppgfile)
            // CSV形式でデータを書き込む
            for (data in ppgValueList) {
                //val record = data.joinToString(",") // カンマで要素を結合
                fileWriter.write("$data\n") // 改行を追加
                Log.d("aa", "Aa")
            }
            fileWriter.close()
            Log.d("TAG", "CSVデータをストレージに保存しました!：$ppgFileName")
            //storageに保存
            //storage内にパスを参照
            val uploadFileRef = dataRef?.child("data/${ppgFileName}")
            //ローカルのファイルを参照しに行く
            var newpath = "$filepath/$ppgFileName"
            var file = Uri.fromFile(File(newpath))
            Log.d("newpath", newpath)
            // /storage/emulated/0/Android/data/com.example.ppgsensor/files/Documents./ppg_data1.csv
//            val uploadTask = uploadFileRef?.putFile(file)
//            uploadTask?.addOnSuccessListener {
//                Log.d("FirebaseStorage", "CSVファイルが正常にアップロードされました。")
//            }?.addOnFailureListener { e ->
//                Log.e("FirebaseStorage", "CSVファイルのアップロード中にエラーが発生しました。", e)
//            }

        } catch (e: IOException) {
            Log.d("error", "$e.printStackTrace()")
        }





    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // センサーの精度が変更された場合の処理
    }

    //センサから情報が得られたときに実行される
    override fun onSensorChanged(event: SensorEvent?) {
        //.?はセーフコール演算子、nullでなければ、処理が実行される. eventオブジェクトにセンサイベントが入ってる。
//        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
//        val timestamp = System.currentTimeMillis()
//        val timestampFormatted = dateFormat.format(Date(timestamp))
        event?.let {
            Log.d("sensorevent", "${it.sensor.type}") //65572と出力される。
            if(it.sensor.type == 65572) {
                ppgValue = it.values[0]
                Log.d("ppg", "$ppgValue")
//                ppgValueList.add(timestampFormatted.toFloat())
                ppgValueList.add(ppgValue)
            }
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                accValueX = it.values[0]
                accValueY = it.values[1]
                accValueZ = it.values[2]
//                accValueList.add(timestampFormatted.toFloat())
                accValueList.add(accValueX)
                Log.d("acc", "$accValueX, $accValueY, $accValueZ")
            }
        }
    }

    private val onToggleListening: () -> Unit = {
        isListening = !isListening
        isRecording = !isRecording
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

