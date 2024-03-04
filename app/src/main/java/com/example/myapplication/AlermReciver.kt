package com.example.myapplication

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_MUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import org.w3c.dom.CharacterData


class AlermReciver : BroadcastReceiver() {
    private val TAG = this.javaClass.simpleName
    var manager: NotificationManager? = null
    var builder: NotificationCompat.Builder? = null

    //수신되는 인텐트 - The Intent being received.
    override fun onReceive(context: Context, intent: Intent) {
        Log.e(TAG, "onReceive 알람이 들어옴!!")
        val contentValue = intent.getStringExtra("날씨알리미")
        Log.e(TAG, "onReceive contentValue값 확인 : $contentValue")
        builder = null

        //푸시 알림을 보내기위해 시스템에 권한을 요청하여 생성
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        //안드로이드 오레오 버전 대응
        builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager!!.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            NotificationCompat.Builder(context, CHANNEL_ID)
        } else {
            NotificationCompat.Builder(context)
        }

        //알림창 클릭 시 지정된 activity 화면으로 이동
        val intent2 = Intent(context, MainActivity::class.java)

        // FLAG_UPDATE_CURRENT ->
        // 설명된 PendingIntent가 이미 존재하는 경우 유지하되, 추가 데이터를 이 새 Intent에 있는 것으로 대체함을 나타내는 플래그입니다.
        // getActivity, getBroadcast 및 getService와 함께 사용
        val pendingIntent = PendingIntent.getActivity(
            context, 101, intent2,
            FLAG_MUTABLE
        )

        //알림창 제목
        builder!!.setContentTitle(contentValue) //회의명노출
        builder?.setContentText("오전 날씨 : " + String(Character.toChars(0x2601)) +
                " /  오후 날씨 : " + String(Character.toChars(0x2601)))
        //cloud 0x2601 rain : 0x1F327 snow : 0x2744 흐림 : 0x26C5  비눈 : 0x1F328
        //알림창 아이콘
        builder!!.setSmallIcon(R.drawable.wehtericon)
        //알림창 터치시 자동 삭제
        builder!!.setAutoCancel(true)
        builder!!.setContentIntent(pendingIntent)

        //푸시알림 빌드
        val notification: Notification = builder!!.build()

        //NotificationManager를 이용하여 푸시 알림 보내기
        manager!!.notify(1, notification)
    }

    companion object {
        //오레오 이상은 반드시 채널을 설정해줘야 Notification이 작동함
        private const val CHANNEL_ID = "channel1"
        private const val CHANNEL_NAME = "Channel1"
    }
}