package com.example.opencv_example

import android.content.res.AssetManager
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.googlecode.tesseract.android.TessBaseAPI

import kotlinx.android.synthetic.main.activty_2.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class Main2 : AppCompatActivity() {

    lateinit var tessBaseAPI: TessBaseAPI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activty_2)



        val drawable = getDrawable(R.drawable.test_3)
        val bitmapDrawable = drawable as BitmapDrawable
        val bitmap = bitmapDrawable.bitmap

        tessBaseAPI = TessBaseAPI()
        var dir: String = filesDir.toString() + "/tesseract"
//        var dir: String = getExternalFilesDir().toString() + "/tesseract"
        Log.d("tesseDir", dir.toString())
        if(checkLanguageFile(dir +"/tessdata"))
            tessBaseAPI.init(dir,"kor")
        Thread {
            runOnUiThread {
                tessBaseAPI.setImage(bitmap)
                var result = tessBaseAPI.utF8Text
                TextView2.text = result.toString()
            }
        }.start()

    }

    fun checkLanguageFile(dir : String):Boolean{
        var file : File = File(dir)
        if(!file.exists() && file.mkdirs()){
            createFiles(dir)
        }else if(file.exists()){
            var filePath : String = dir + "/kor.traineddata"
            var langDataFile : File = File(filePath)
            if(!langDataFile.exists())
                createFiles(dir)
        }
        return true
    }

    fun createFiles(dir : String){
        var assetMgr : AssetManager = this.assets

        var inputStream : InputStream? = null
        var outputStream : OutputStream? = null

        try{
            inputStream = assetMgr.open("kor.traineddata")
            var destFile : String = dir + "/kor.traineddata"
            outputStream = FileOutputStream(destFile)

            var buffer : ByteArray = ByteArray(1024)

            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            inputStream.close()
            outputStream.flush()
            outputStream.close()
        }catch (e : Exception){
            e.printStackTrace()
        }
    }

}