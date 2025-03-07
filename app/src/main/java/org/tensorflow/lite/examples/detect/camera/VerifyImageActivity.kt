package org.tensorflow.lite.examples.detect.camera

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.RoundedCornerTreatment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_verification.*
import kotlinx.android.synthetic.main.dialog_report.*
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import org.tensorflow.lite.examples.detect.AnimationFab
import org.tensorflow.lite.examples.detect.R
import org.tensorflow.lite.examples.detect.network.*
import org.tensorflow.lite.examples.detect.onboard.OnBoardActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class VerifyImageActivity : AppCompatActivity() {
    private val api = RetrofitClient.create(FlaskApi::class.java)
    private var resultStringBreed = ""
    private var resultStringMuzzle = ""
    private var resultStringSafety = ""
    private var networkCheck = ""

    @RequiresApi(Build.VERSION_CODES.O)
    val current = LocalDateTime.now()
    @RequiresApi(Build.VERSION_CODES.O)
    val formatter = DateTimeFormatter.ISO_DATE
    @RequiresApi(Build.VERSION_CODES.O)
    val formatted = current.format(formatter)

    var imageView: ImageView? = null
    var imageUri: Uri? = null
    val camRequestId = 1222
    lateinit var bodyImage : MultipartBody.Part
    var userNick = "unknown"

    var userUID : String? = null
    private lateinit var auth: FirebaseAuth
    private val database = Firebase.database
    private val db = FirebaseFirestore.getInstance()
    lateinit var uri: Uri
    var verityValueKey : String = ""
    val nickDB = database.getReference("Nickname")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_image)
        val bottomAppBar = findViewById<BottomAppBar>(R.id.bottomAppBar)
        setSupportActionBar(bottomAppBar)

        auth = Firebase.auth
        userUID = auth.currentUser?.uid







        // 닉네임 정보 가져오기
        nickDB.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                //유저 닉네임 데이터 수신
                var nickData: Map<String, String> = snapshot.value as Map<String, String>
                userNick = when (nickData[userUID]) {
                    null -> "???"
                    else -> nickData[userUID]!!
                }

                Log.e("userNick22", userNick)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Error", "error nickname")
            }
        })



        val testTxt = findViewById<TextView>(R.id.test)
        val btnVerify = findViewById<FloatingActionButton>(R.id.fab)
        val btnBreed = findViewById<Button>(R.id.breedGetBtn)
        val btnMuzzle = findViewById<Button>(R.id.muzzleGetBtn)
        val btnSafety = findViewById<Button>(R.id.safetyGetBtn)
        val cameraBtn = findViewById<FloatingActionButton>(R.id.fab)
        val serverBtn = findViewById<Button>(R.id.toServerBtn)
        imageView = findViewById(R.id.imageView2)

        // 하단 바
        val bottomBarBackground = bottomAppBar.background as MaterialShapeDrawable
        bottomBarBackground.shapeAppearanceModel = bottomBarBackground.shapeAppearanceModel
            .toBuilder()
            .setTopRightCorner(RoundedCornerTreatment()).setTopRightCornerSize(RelativeCornerSize(0.4f))
            .setTopLeftCorner(RoundedCornerTreatment()).setTopLeftCornerSize(RelativeCornerSize(0.4f))
            .build()


        // 카메라 클릭 시
        cameraBtn.setOnClickListener{
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val imagePath = createImage()
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imagePath)
            startActivityForResult(intent, camRequestId)
        }

        // 서버로 보내기
        serverBtn.setOnClickListener {
            sendFile("file",bodyImage)
        }


        // 통신 테스트
        api.getTest().enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                testTxt.text = "Success"
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                testTxt.text = "Fail"
            }

        })



        // 견종 결과 이미지로 확인
        btnBreed.setOnClickListener {
            Log.e("resultStringBreed2", resultStringBreed)
            val intent = Intent(this, DeepLearningResultActivity::class.java)
            intent.putExtra("resultStringBreedUri", resultStringBreed)
            startActivity(intent)
        }

        // 입마개 결과 이미지로 확인
        btnMuzzle.setOnClickListener {
            Log.e("resultStringMuzzle2", resultStringMuzzle)
            val intent = Intent(this, DeepLearningResultActivity::class.java)
            intent.putExtra("resultStringMuzzleUri", resultStringMuzzle)
            startActivity(intent)
        }

        // 목줄 결과 이미지로 확인
        btnSafety.setOnClickListener {
            Log.e("resultStringSafety2", resultStringSafety)
            val intent = Intent(this, DeepLearningResultActivity::class.java)
            intent.putExtra("resultStringSafetyUri", resultStringSafety)
            startActivity(intent)
        }

    }


    // firebase Storage 에 업로드
    private fun uploadImage(uri: Uri, str : String) {

        val storage = FirebaseStorage.getInstance().getReference("images/${verityValueKey}/" + str)
        storage.putFile(uri).addOnSuccessListener {
            Toast.makeText(this, "!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {

        }

    }


    // realtimeDB 에 넣기
    @RequiresApi(Build.VERSION_CODES.O)
    fun firebaseSetResult(resultBreed : Boolean, resultMuzzle : Boolean, resultSafety : Boolean) {
        val verifyRef = database.getReference("verify")
        val verifyModel = userUID?.let {
            it->
            VerityData(
                resultBreed = resultBreed,
                resultMuzzle = resultMuzzle,
                resultSafety = resultSafety,
                uid = it,
                nickname = userNick,
                date = formatted
            )
        }
        val verifyRefPush = verifyRef.push()
        verityValueKey = verifyRefPush.key.toString()
        verifyModel?.verifyId = verifyRefPush.key.toString() //랜덤 생성

        verifyRefPush.setValue(verifyModel)
        Log.d("verifyId", "${verifyModel?.verifyId}")
//        verifyRef.child(userUID!!).setValue()
    }

    fun sendFirstSetting(uri : Uri) {
        val file = File(absolutelyPath(uri, this))
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val bodyImage = MultipartBody.Part.createFormData("file", file.name, requestFile)
    }

    // 카메라 실행
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == camRequestId) {
            if (resultCode == RESULT_OK) {
                imageView!!.setImageURI(imageUri)

                val uri : Uri? = imageUri
                val file = File(absolutelyPath(uri, this))
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                bodyImage = MultipartBody.Part.createFormData("file", file.name, requestFile)

                Log.d("TAG",file.name)

                //sendFile("file",bodyImage)
            }
        }
    }

    // 이미지 생성
    private fun createImage(): Uri? {
        var uri: Uri? = null
        val resolver = contentResolver
        uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            // DCIM
            // picture
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val imgName = System.currentTimeMillis().toString()
        val contentValues = ContentValues()
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, "$imgName.jpg")
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + "My Images/")
        val finalUri = resolver.insert(uri, contentValues)
        imageUri = finalUri
        return finalUri
    }


    // 서버로 이미지 보내기
    private fun sendFile(userCd : String, image : MultipartBody.Part) {
        val service = RetrofitClient.create(FlaskApi::class.java) //레트로핏 통신 설정
        val call = service.postFile(userCd, image) //통신 API 패스 설정
        val dialog = LoadingDialog(this)
        val dialogReport = ReportDialog(this)

        // 다이얼로그 보여주기
        dialog.show()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)

        call.enqueue(object : Callback<FlaskDto> {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResponse(call: Call<FlaskDto>, response: Response<FlaskDto>) {
                if (response.isSuccessful) {
                    Log.d("로그 ", "" + response.body().toString())
                    Toast.makeText(applicationContext, "통신 성공", Toast.LENGTH_SHORT).show()

                    val resultBreed = response.body()?.result_breed
                    val resultBreedImgPath = response.body()?.result_breed_imgPath
                    val resultMuzzle = response.body()?.result_muzzle
                    val resultMuzzleImgPath = response.body()?.result_muzzle_imgPath
                    val resultSafety = response.body()?.result_safety
                    val resultSafetyImgPath = response.body()?.result_safety_imgPath

                    Log.e("resultBreedImgPath", resultBreedImgPath.toString())
                    Log.e("resultMuzzleImgPath", resultMuzzleImgPath.toString())
                    Log.e("resultSafetyImgPath", resultSafetyImgPath.toString())

                    if (resultBreedImgPath != null) {
                        resultStringBreed = resultBreedImgPath
                    }
                    if (resultMuzzleImgPath != null) {
                        resultStringMuzzle = resultMuzzleImgPath
                    }
                    if (resultSafetyImgPath != null) {
                        resultStringSafety = resultSafetyImgPath
                    }

                    val result1 = findViewById<TextView>(R.id.resultBreed)
                    // val result2 = findViewById<TextView>(R.id.result2)
                    val result3 = findViewById<TextView>(R.id.resultMuzzle)
                    // val result4 = findViewById<TextView>(R.id.result4)
                    val result5 = findViewById<TextView>(R.id.resultSafety)
                    // val result6 = findViewById<TextView>(R.id.result6)


                    // 버튼 활성화
                    val btnBreed = findViewById<Button>(R.id.breedGetBtn)
                    val btnMuzzle = findViewById<Button>(R.id.muzzleGetBtn)
                    val btnSafety = findViewById<Button>(R.id.safetyGetBtn)
                    btnBreed.isEnabled = true
                    btnMuzzle.isEnabled = true
                    btnSafety.isEnabled = true


                    if (resultBreed == true) {
                        result1.text = "맹견"
                    } else result1.text = "미인식"

                    if (resultMuzzle == true) {
                        result3.text = "착용"
                    } else result3.text = "미착용"

                    if (resultSafety == true) {
                        result5.text = "착용"
                    } else result5.text = "미착용"
//                    result1.text = resultBreed.toString()
//                    // result2.text = resultBreedImgPath
//                    result3.text = resultMuzzle.toString()
//                    // result4.text = resultMuzzleImgPath
//                    result5.text = resultSafety.toString()
                    // result6.text = resultSafetyImgPath
                    Log.e("userNick", userNick)
                    // 다이얼로그 지우기






                    if (resultBreed != null) {
                        if (resultMuzzle != null) {
                            if (resultSafety != null) {
                                firebaseSetResult(resultBreed, resultMuzzle, resultSafety)
                            }
                        }
                    }


                    // 딜레이 줘서 파일 다운로드
                    getImageResult(resultBreedImgPath!!, "breed")

                    Handler().postDelayed({
                        getImageResult(resultMuzzleImgPath!!, "muzzle")
                    }, 3000)

                    Handler().postDelayed({
                        getImageResult(resultSafetyImgPath!!, "safety")
                    }, 3000)
//                    getImageResult(resultMuzzleImgPath!!, "muzzle")
//                    getImageResult(resultSafetyImgPath!!, "safety")

                    dialog.dismiss()


                    if (resultBreed == true && resultMuzzle == false && resultSafety == false) {
                        dialogReport.show()

                        dialogReport.report_btn.setOnClickListener {
                            Toast.makeText(applicationContext, "신고 접수", Toast.LENGTH_SHORT).show()
                            dialogReport.dismiss()
                        }

                        dialogReport.exit_btn.setOnClickListener {
                            dialogReport.dismiss()
                        }
                    }

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


    private fun report() {

    }

    // 비트맵 Uri로 변환
    private fun getImageUri(context: Context, inImage: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path =
            MediaStore.Images.Media.insertImage(context.contentResolver, inImage, "Title", null)
        return Uri.parse(path)
    }

    // 이미지 결과값 받기
    private fun getImageResult(resultString: String, value : String) {
        api.getImage(resultString)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    val toString = response.body()?.byteStream()

                    val decodeStream = BitmapFactory.decodeStream(toString)
//                    val image = findViewById<ImageView>(R.id.imageView2)
//                    image.setImageBitmap(decodeStream)

                    val imageUri1 = getImageUri(applicationContext, decodeStream)
                    if (imageUri1 != null) {


                        uploadImage(imageUri1, value)
                    }

                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Log.d("로그 fail", t.message.toString())

                    // 실패시 다시 통신
                    getImageResult(resultString, value)
                }
            })
    }


    // 파일 관련
    var launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imagePath = result.data!!.data

            val file = File(absolutelyPath(imagePath, this))
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            Log.d("TAG",file.name)
            imageView!!.setImageURI(imagePath)

            sendFile("file",body)
        }
    }

    // 이미지 앱 선택
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

    // 화면 아래 메뉴 바
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.navigation_menu_verity, menu)
        return true
    }

    override fun onStart() {
        // 애니메이션 작동
        super.onStart()
        Handler().postDelayed({
            fab.show()
        }, 450)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { // 홈으로 돌아가기
                fab.hide(AnimationFab.addVisibilityChanged)
                Handler().postDelayed({
                    finish()
                }, 300)
            }
            R.id.app_bar_album ->{
                getImage()
            }
        }
        return true
    }
}