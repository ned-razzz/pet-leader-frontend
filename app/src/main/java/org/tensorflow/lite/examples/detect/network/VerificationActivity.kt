package org.tensorflow.lite.examples.detect.network

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.tensorflow.lite.examples.detect.R
import org.tensorflow.lite.examples.detect.databinding.ActivityVerificationBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class VerificationActivity : AppCompatActivity() {
    private val api = RetrofitClient.create(FlaskApi::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        val testTxt = findViewById<TextView>(R.id.test)
        val btnVerify = findViewById<Button>(R.id.btn_verification)


        api.getTest().enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                testTxt.text = "Success"
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                testTxt.text = "Fail"
            }

        })

        btnVerify.setOnClickListener {
            getImage()
        }
    }

    private fun sendFile(userCd : String, image : MultipartBody.Part) {
        val service = RetrofitClient.create(FlaskApi::class.java) //레트로핏 통신 설정
        val call = service.postFile(userCd, image)!! //통신 API 패스 설정
        val dialog = LoadingDialog(this)

        // 다이얼로그 보여주기
        dialog.show()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)

        call.enqueue(object : Callback<FlaskDto> {
            override fun onResponse(call: Call<FlaskDto>, response: Response<FlaskDto>) {
                if (response.isSuccessful) {
                    Log.d("로그 ", "" + response?.body().toString())
                    Toast.makeText(applicationContext, "통신 성공", Toast.LENGTH_SHORT).show()

                    val resultBreed = response.body()?.result_breed
                    val resultBreedImgPath = response.body()?.result_breed_imgPath
                    val resultMuzzle = response.body()?.result_muzzle
                    val resultMuzzleImgPath = response.body()?.result_muzzle_imgPath
                    val resultSafety = response.body()?.result_safety
                    val resultSafetyImgPath = response.body()?.result_safety_imgPath


                    val result1 = findViewById<TextView>(R.id.result1)
                    val result2 = findViewById<TextView>(R.id.result2)
                    val result3 = findViewById<TextView>(R.id.result3)
                    val result4 = findViewById<TextView>(R.id.result4)
                    val result5 = findViewById<TextView>(R.id.result5)
                    val result6 = findViewById<TextView>(R.id.result6)

                    result1.text = resultBreed.toString()
                    result2.text = resultBreedImgPath
                    result3.text = resultMuzzle.toString()
                    result4.text = resultMuzzleImgPath
                    result5.text = resultSafety.toString()
                    result6.text = resultSafetyImgPath

                    // 다이얼로그 지우기
                    dialog.dismiss()


                } else {
                    Toast.makeText(applicationContext, "통신 실패", Toast.LENGTH_SHORT).show()

                    // 다이얼로그 지우기
                    dialog.dismiss()
                }
            }

            override fun onFailure(call: Call<FlaskDto>, t: Throwable) {
                Log.d("로그 ", t.message.toString())

                // 다이얼로그 지우기
                dialog.dismiss()
            }
        })
    }

    var launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imagePath = result.data!!.data

            val file = File(absolutelyPath(imagePath, this))
            val requestFile = RequestBody.create("image/*".toMediaTypeOrNull(), file)
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            Log.d("TAG",file.name)

            sendFile("file",body)
        }
    }
    fun getImage(){
        Log.d("mediaFile","사진변경 호출")
        val chooserIntent = Intent(Intent.ACTION_CHOOSER)
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/* video/*"
        chooserIntent.putExtra(Intent.EXTRA_INTENT, intent)
        chooserIntent.putExtra(Intent.EXTRA_TITLE,"사용할 앱을 선택해주세요.")
        launcher.launch(chooserIntent)
    }

    // 절대경로 변환
    fun absolutelyPath(path: Uri?, context : Context): String {
        var proj: Array<String> = arrayOf(MediaStore.Images.Media.DATA)
        var c: Cursor? = context.contentResolver.query(path!!, proj, null, null, null)
        var index = c?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        c?.moveToFirst()

        var result = c?.getString(index!!)

        return result!!
    }
}