package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Response
import java.io.IOException
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.*


class MainActivity : AppCompatActivity(){
    private var gpsTracker: AlermReciver? = null
    private var mBinding: ActivityMainBinding? = null
    // 매번 null 체크를 할 필요 없이 편의성을 위해 바인딩 변수 재 선언
    private val binding get() = mBinding!!

    //권한
    private val GPS_ENABLE_REQUEST_CODE = 2001
    private val PERMISSIONS_REQUEST_CODE = 100
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    //날씨
    protected var locationManager: LocationManager? = null
    private var address : String? = null
    lateinit var weatherRecyclerView : RecyclerView
    private var base_date = "20210510"  // 발표 일자
    private var base_time = "1400"      // 발표 시각
    private var nx = "55"               // 예보지점 X 좌표
    private var ny = "127"              // 예보지점 Y 좌표

    //현재 시간,분 변수선언
    private var currHour : Int? = null
    private var currMinute : Int? = null

    //시스템에서 알람 서비스를 제공하도록 도와주는 클래스
    //특정 시점에 알람이 울리도록 도와준다
    private var alarmManager : AlarmManager? = null

    private var timeCallbackMethod : TimePickerDialog.OnTimeSetListener? = null

    @RequiresApi(api = Build.VERSION_CODES.O)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)



        // 리사이클러 뷰 매니저 설정
        binding.weatherRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.weatherRecyclerView.setHasFixedSize(true)

        // 오늘 날짜 텍스트뷰 설정
        binding.tvDate.text = SimpleDateFormat("MM월 dd일", Locale.getDefault()).format(Calendar.getInstance().time) + " 날씨"



        // nx, ny지점의 날씨 가져와서 설정하기
        setWeather(nx, ny)

        // <새로고침> 버튼 누를 때 날씨 정보 다시 가져오기
        binding.btnRefresh.setOnClickListener {
            setWeather(nx, ny)
        }

        //현재 시간기준으로 몇시 몇분인지 구하기
        val now: LocalTime = LocalTime.now()
        currHour = now.getHour()
        currMinute = now.getMinute()


        //푸시알림을 보내기 위해, 시스템에서 알림 서비스를 생성해주는 코드
        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        binding.imgBtnChangeTime.setOnClickListener(View.OnClickListener { //스피너모드 타임피커
            val dialog = TimePickerDialog(
                this@MainActivity,
                com.google.android.material.R.style.Theme_Design_Light_NoActionBar,
                timeCallbackMethod, currHour!!, currMinute!!, false
            )

            //다이알로그 타이틀 설정
            dialog.setTitle("알람 시간 설정")

            //알람 설정후 변경된 시간변경 및 토스트 팝업 발생

            //기존테마의 배경을 없앤다
            dialog.window!!.setBackgroundDrawableResource(R.color.white)
            dialog.show()
        })

        timeCallbackMethod = OnTimeSetListener { view, hourOfDay, minute ->

            //변경된 시간으로 textview 업데이트
            binding.txtStartTime.setText(formatTime("$hourOfDay:$minute"))

            //알람 등록 처리
            setNotice("$hourOfDay:$minute:00")
        }

        setContentView(binding.root)
    }

    //현재위치
    private fun getLatLng(): Location {
        var currentLatLng: Location? = null
        var hasFineLocationPermission = ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION)
        var hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_COARSE_LOCATION)

        if(hasFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
            hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED){
            val locatioNProvider = LocationManager.GPS_PROVIDER
            currentLatLng = locationManager?.getLastKnownLocation(locatioNProvider)
        }else{
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])){
                Toast.makeText(this, "앱을 실행하려면 위치 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
            }else{
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
            }
            currentLatLng = getLatLng()
        }
        return currentLatLng!!
    }

    fun getCurrentAddress(latitude: Double, longitude: Double): String? {

        //지오코더... GPS를 주소로 변환
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses: List<Address>?
        addresses = try {
            geocoder.getFromLocation(
                latitude,
                longitude,
                7
            )
        } catch (ioException: IOException) {
            //네트워크 문제
            Toast.makeText(this, "지오코더 서비스 사용불가", Toast.LENGTH_LONG).show()
            return "지오코더 서비스 사용불가"
        } catch (illegalArgumentException: IllegalArgumentException) {
            Toast.makeText(this, "잘못된 GPS 좌표", Toast.LENGTH_LONG).show()
            return "잘못된 GPS 좌표"
        }
        if (addresses == null || addresses.size == 0) {
            Toast.makeText(this, "주소 미발견", Toast.LENGTH_LONG).show()
            return "주소 미발견"
        }
        val address = addresses[0]
        return """
             ${address.getAddressLine(0)}
             
             """.trimIndent()
    }

    fun checkRunTimePermission() {

        //런타임 퍼미션 처리
        // 1. 위치 퍼미션을 가지고 있는지 체크합니다.
        val hasFineLocationPermission: Int = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val hasCoarseLocationPermission: Int = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
            hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED
        ) {

            // 2. 이미 퍼미션을 가지고 있다면
            // ( 안드로이드 6.0 이하 버전은 런타임 퍼미션이 필요없기 때문에 이미 허용된 걸로 인식합니다.)


            // 3.  위치 값을 가져올 수 있음
        } else {  //2. 퍼미션 요청을 허용한 적이 없다면 퍼미션 요청이 필요합니다. 2가지 경우(3-1, 4-1)가 있습니다.

            // 3-1. 사용자가 퍼미션 거부를 한 적이 있는 경우에는
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@MainActivity,
                    REQUIRED_PERMISSIONS[0]
                )
            ) {

                // 3-2. 요청을 진행하기 전에 사용자가에게 퍼미션이 필요한 이유를 설명해줄 필요가 있습니다.
                Toast.makeText(this@MainActivity, "이 앱을 실행하려면 위치 접근 권한이 필요합니다.", Toast.LENGTH_LONG)
                    .show()
                // 3-3. 사용자게에 퍼미션 요청을 합니다. 요청 결과는 onRequestPermissionResult에서 수신됩니다.
                ActivityCompat.requestPermissions(
                    this@MainActivity, REQUIRED_PERMISSIONS,
                    PERMISSIONS_REQUEST_CODE
                )
            } else {
                // 4-1. 사용자가 퍼미션 거부를 한 적이 없는 경우에는 퍼미션 요청을 바로 합니다.
                // 요청 결과는 onRequestPermissionResult에서 수신됩니다.
                ActivityCompat.requestPermissions(
                    this@MainActivity, REQUIRED_PERMISSIONS,
                    PERMISSIONS_REQUEST_CODE
                )
            }
        }
    }

    //여기부터는 GPS 활성화를 위한 메소드들
    private fun showDialogForLocationServiceSetting() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage(
            """
            앱을 사용하기 위해서는 위치 서비스가 필요합니다.
            위치 설정을 수정하실래요?
            """.trimIndent()
        )
        builder.setCancelable(true)
        builder.setPositiveButton("설정", DialogInterface.OnClickListener { dialog, id ->
            val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivityForResult(callGPSSettingIntent, GPS_ENABLE_REQUEST_CODE)
        })
        builder.setNegativeButton("취소",
            DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
        builder.create().show()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            GPS_ENABLE_REQUEST_CODE ->
                //사용자가 GPS 활성 시켰는지 검사
                if (checkLocationServicesStatus()) {
                    if (checkLocationServicesStatus()) {
                        Log.d("@@@", "onActivityResult : GPS 활성화 되있음")
                        checkRunTimePermission()
                        return
                    }
                }
        }
    }

    fun checkLocationServicesStatus(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    fun getAddress(mContext: Context?, lat: Double, lng: Double): String? {
        var nowAddr = "현재 위치를 확인 할 수 없습니다."
        val geocoder = Geocoder(mContext!!, Locale.KOREA)
        val address: List<Address>?
        try {
            if (geocoder != null) {
                address = geocoder.getFromLocation(lat, lng, 1)
                if (address != null && address.size > 0) {
                    nowAddr = address[0].getAddressLine(0).toString()
                }
            }
        } catch (e: IOException) {
            Toast.makeText(mContext, "주소를 가져 올 수 없습니다.", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
        return nowAddr
    }
    // 날씨 가져와서 설정하기
    private fun setWeather(nx : String, ny : String) {
        // 준비 단계 : base_date(발표 일자), base_time(발표 시각)
        // 현재 날짜, 시간 정보 가져오기
        val cal = Calendar.getInstance()
        base_date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time) // 현재 날짜
        val timeH = SimpleDateFormat("HH", Locale.getDefault()).format(cal.time) // 현재 시각
        val timeM = SimpleDateFormat("HH", Locale.getDefault()).format(cal.time) // 현재 분
        // API 가져오기 적당하게 변환
        base_time = getBaseTime(timeH, timeM)
        // 현재 시각이 00시이고 45분 이하여서 baseTime이 2330이면 어제 정보 받아오기
        if (timeH == "00" && base_time == "2330") {
            cal.add(Calendar.DATE, -1).toString()
            base_date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)
        }

        // 날씨 정보 가져오기
        // (한 페이지 결과 수 = 60, 페이지 번호 = 1, 응답 자료 형식-"JSON", 발표 날싸, 발표 시각, 예보지점 좌표)
        val call = ApiObject.retrofitService.GetWeather(60, 1, "JSON", base_date, base_time, nx, ny)
        binding.local.text = "남양주시"
        // 비동기적으로 실행하기
        call.enqueue(object : retrofit2.Callback<WEATHER> {
            // 응답 성공 시
            override fun onResponse(call: Call<WEATHER>, response: Response<WEATHER>) {
                if (response.isSuccessful) {
                    // 날씨 정보 가져오기
                    val it: List<ITEM> = response.body()!!.response.body.items.item

                    // 현재 시각부터 1시간 뒤의 날씨 6개를 담을 배열
                    val weatherArr = arrayOf(WeatherDataModel(), WeatherDataModel(), WeatherDataModel(), WeatherDataModel(), WeatherDataModel(), WeatherDataModel())

                    // 배열 채우기
                    var index = 0
                    val totalCount = response.body()!!.response.body.totalCount - 1
                    for (i in 0..totalCount) {
                        index %= 6
                        when(it[i].category) {
                            "PTY" -> weatherArr[index].rainType = it[i].fcstValue     // 강수 형태
                            "REH" -> weatherArr[index].humidity = it[i].fcstValue     // 습도
                            "SKY" -> weatherArr[index].sky = it[i].fcstValue          // 하늘 상태
                            "T1H" -> weatherArr[index].temp = it[i].fcstValue         // 기온
                            else -> continue
                        }
                        index++
                    }

                    // 각 날짜 배열 시간 설정
                    for (i in 0..5) weatherArr[i].fcstTime = "    " + it[i].fcstTime
                    binding.tmp.text = weatherArr[0].temp + "°c"


                    if(weatherArr[0].sky.equals("4")) {
                        binding.temper.text = "흐림"
                        // import android.R을 하면 새로 추라한 것을 알수 없음
                        binding.imageview.setImageResource(R.drawable.ccloud)
                    }
                    else if(weatherArr[0].sky.equals("1")) {
                        binding.temper.text = "맑음"
                        binding.imageview.setImageResource(R.drawable.ssun)
                    }
                    else if(weatherArr[0].sky.equals("3")) {
                        binding.temper.text = "구름 많음"
                        binding.imageview.setImageResource(R.drawable.ccloudmany)
                    }
                    else if(weatherArr[0].rainType.equals("1")){
                        binding.temper.text = "비"
                        binding.imageview.setImageResource(R.drawable.rrain)
                    }
                    else if(weatherArr[0].rainType.equals("2")){
                        binding.temper.text = "비/눈"
                        binding.imageview.setImageResource(R.drawable.ssnowandrain)
                    }
                    else if(weatherArr[0].rainType.equals("3")){
                        binding.temper.text = "눈"
                        binding.imageview.setImageResource(R.drawable.ssnow)
                    }
                    else{
                        binding.temper.text = "맑음"
                        binding.imageview.setImageResource(R.drawable.ssun)
                    }



                    // 리사이클러 뷰에 데이터 연결
                    binding.weatherRecyclerView.adapter = WeatherAdapter(weatherArr)

                }
            }

            // 응답 실패 시
            override fun onFailure(call: Call<WEATHER>, t: Throwable) {

                Log.d("api fail", t.message.toString())
            }
        })
    }

    // baseTime 설정하기
    private fun getBaseTime(h : String, m : String) : String {
        var result = ""

        // 45분 전이면
        if (m.toInt() < 45) {
            // 0시면 2330
            if (h == "00") result = "2330"
            // 아니면 1시간 전 날씨 정보 부르기
            else {
                var resultH = h.toInt() - 1
                // 1자리면 0 붙여서 2자리로 만들기
                if (resultH < 10) result = "0" + resultH + "30"
                // 2자리면 그대로
                else result = resultH.toString() + "30"
            }
        }
        // 45분 이후면 바로 정보 받아오기
        else result = h + "30"

        return result
    }


    //날짜 포맷 변환
    // HH:mm => 오전/오후 hh:mm
    fun formatTime(timeValue: String?): String? {
        val reqDateFormat: DateFormat = SimpleDateFormat("HH:mm")
        val resDateFormat: DateFormat = SimpleDateFormat("a hh:mm", Locale.KOREAN)
        var datetime: Date? = null
        try {
            //문자열을 파싱해서 Date객체를 만들어준다
            datetime = reqDateFormat.parse(timeValue)
        } catch (e: ParseException) {
            //패턴과다른 문자열이 입력되면 parse exception 발생된다
            e.printStackTrace()
        }
        return resDateFormat.format(datetime)
    }

    private fun setNotice(alarmTimeValue: String) {

        //알람을 수신할 수 있도록 하는 리시버로 인텐트 요청
        val receiverIntent = Intent(this, AlermReciver::class.java)
        receiverIntent.putExtra("content", "알람등록 테스트")
        /**
         * PendingIntent란?
         * - Notification으로 작업을 수행할 때 인텐트가 실행되도록 합니다.
         * Notification은 안드로이드 시스템의 NotificationManager가 Intent를 실행합니다.
         * 즉 다른 프로세스에서 수행하기 때문에 Notification으로 Intent수행시 PendingIntent의 사용이 필수 입니다.
         */
        /**
         * 브로드캐스트로 실행될 pendingIntent선언 한다.
         * Intent가 새로 생성될때마다(알람을 등록할 때마다) intent값을 업데이트 시키기 위해, FLAG_UPDATE_CURRENT 플래그를 준다
         * 이전 알람을 취소시키지 않으려면 requestCode를 다르게 줘야 한다.
         */
        val pendingIntent =
            PendingIntent.getBroadcast(this, 123, receiverIntent,  FLAG_MUTABLE)


        //등록한 알람날짜 포맷을 밀리초로 변경한기 위한 코드
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val now: LocalDate = LocalDate.now() //현재시간 구하기
        var datetime: Date? = null
        try {
            datetime = dateFormat.parse(now.toString() + " " + alarmTimeValue)
        } catch (e: ParseException) {
            e.printStackTrace()
        }

        //date타입으로 변경된 알람시간을 캘린더 타임에 등록
        val calendar: Calendar = Calendar.getInstance()
        calendar.setTime(datetime)

        //알람시간 설정
        //param 1)알람의 타입
        //param 2)알람이 울려야 하는 시간(밀리초)을 나타낸다.
        //param 3)알람이 울릴 때 수행할 작업을 나타냄
        alarmManager!!.set(AlarmManager.RTC, calendar.getTimeInMillis(), pendingIntent)
    }


    override fun onDestroy() {
        mBinding = null
        super.onDestroy()
    }

}