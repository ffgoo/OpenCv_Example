package com.example.opencv_example


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.*


class MainActivity : AppCompatActivity() {

    lateinit var tessBaseAPI: TessBaseAPI

    var ORIENTATIONS = SparseIntArray()

    var cameraId: String = ""
    protected var cameraDevice: CameraDevice? = null
    protected var cameraCaptureSessions: CameraCaptureSession? = null
    protected var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageDimension: Size? = null
    private var imageReader: ImageReader? = null
    private val REQUEST_CAMERA_PERMISSION = 200
    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    var imgBase: Bitmap? = null
    var roi: Bitmap? = null
    var TAG = "MAINACTIVITY"

    lateinit var textureView: TextureView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)

        textureView = texture
        assert(textureView != null)
        textureView.surfaceTextureListener = textureListener
        assert(btnTakePicture != null)
        btnTakePicture.setOnClickListener {
            takePicure()
        }

        tessBaseAPI = TessBaseAPI()
        var dir: String = filesDir.toString() + "/tesseract"
//        var dir: String = getExternalFilesDir().toString() + "/tesseract"
        Log.d("tesseDir", dir.toString())
        if(checkLanguageFile(dir +"/tessdata"))
            tessBaseAPI.init(dir,"kor")

    }

    var textureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            //open your camera here
            openCamera()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            // Transform you image captured size according to the surface width and height
        }
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened")
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }


    fun startBackgroundThread(){
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread!!.start()
        var handler = Handler(mBackgroundThread!!.looper)
        mBackgroundHandler = handler
    }



    fun stopBackgroundThread(){
        mBackgroundThread!!.quitSafely()
        try{
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        }catch (e : InterruptedException){
            e.printStackTrace()
        }
    }


    fun takePicure() {
        if (cameraDevice == null) {
            return
        }
        var manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            var characteristics: CameraCharacteristics = manager.getCameraCharacteristics(
                cameraDevice!!.id
            )
            var jpegSizes: Array<Size>? = null
            if (characteristics != null) {
                var map: StreamConfigurationMap =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
            }
            var width: Int = 640
            var height: Int = 480
            if (jpegSizes != null && 0 < jpegSizes.size) {
                width = jpegSizes[0].width
                height = jpegSizes[0].height
            }
            var imageReader: ImageReader =
                ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
            var outputSurface: ArrayList<Surface> = ArrayList<Surface>(2)
            outputSurface.add(imageReader.surface)
            outputSurface.add(Surface(textureView.surfaceTexture))
            var captureBuilder: CaptureRequest.Builder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader.surface)
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            var rotation: Int = windowManager.defaultDisplay.rotation
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))

            var readerListener: ImageReader.OnImageAvailableListener =
                object : ImageReader.OnImageAvailableListener {
                    override fun onImageAvailable(reader: ImageReader?) {
                        var image: Image? = null
                        try {
                            image = reader!!.acquireLatestImage()
                            var buffer: ByteBuffer = image.planes[0].buffer
                            var bytes: ByteArray = ByteArray(buffer.capacity())
                            buffer.get(bytes)

                            var options: BitmapFactory.Options = BitmapFactory.Options()
                            options.inSampleSize = 8
                            var bitmap: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            bitmap = GetRotatedBitmap(bitmap, 90)!!

                            var imgRoi: Bitmap? = null
                            OpenCVLoader.initDebug()

                            var matBase: Mat = Mat()
                            Utils.bitmapToMat(bitmap, matBase)
                            var matGray: Mat = Mat()
                            var matCny: Mat = Mat()

                            Imgproc.cvtColor(matBase, matGray, Imgproc.COLOR_BGR2GRAY)
                            Imgproc.Canny(matGray, matCny, 10.0, 100.0, 3, true);
                            Imgproc.threshold(matGray, matCny, 150.0, 255.0, Imgproc.THRESH_BINARY)

                            var contours: ArrayList<MatOfPoint> = ArrayList()
                            var hierarchy: Mat = Mat()

                            Imgproc.erode(
                                matCny,
                                matCny,
                                Imgproc.getStructuringElement(
                                    Imgproc.MORPH_RECT,
                                    org.opencv.core.Size(6.0, 6.0)
                                )
                            )
                            Imgproc.dilate(
                                matCny,
                                matCny,
                                Imgproc.getStructuringElement(
                                    Imgproc.MORPH_RECT,
                                    org.opencv.core.Size(12.0, 12.0)
                                )
                            )

                            Imgproc.findContours(
                                matCny,
                                contours,
                                hierarchy,
                                Imgproc.RETR_EXTERNAL,
                                Imgproc.CHAIN_APPROX_SIMPLE
                            )
                            Imgproc.drawContours(matBase, contours, -1, Scalar(255.0, 0.0, 0.0), 5)

                            imgBase = Bitmap.createBitmap(
                                matBase.cols(),
                                matBase.rows(),
                                Bitmap.Config.ARGB_8888
                            )
                            Utils.matToBitmap(matBase, imgBase)


                            imgRoi = Bitmap.createBitmap(
                                matCny.cols(),
                                matCny.rows(),
                                Bitmap.Config.ARGB_8888
                            )
                            Utils.matToBitmap(matCny, imgRoi)

                            Thread {
                                runOnUiThread {
                                    //TODO - setImage
                                    imageView.setImageBitmap(imgRoi)
                                }
                            }.start()


                            var idx = 0
                            while (idx >= 0) {
                                idx = hierarchy[0, idx][0].toInt()
                                var matOfPoint: MatOfPoint = contours.get(idx)
                                var rect: Rect? = Imgproc.boundingRect(matOfPoint)

                                if (rect!!.width < 30 || rect!!.height < 30 || rect!!.width <= rect.height || rect.width <= rect.height * 3 || rect.width >= rect.height * 6)
                                    continue

                                roi = Bitmap.createBitmap(
                                    imgRoi,
                                    rect.tl().x.toInt(),
                                    rect.tl().y.toInt(),
                                    rect.width,
                                    rect.height
                                )

                                Thread {
                                    runOnUiThread {
                                        imageResult.setImageBitmap(roi)
                                        AsyncTess(roi)
                                        btnTakePicture.isEnabled = false
                                        btnTakePicture.text = "텍스트 인식중..."
                                    }
                                }.start()
                                break
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            if (image != null) {
                                image.close()
                            }
                        }
                    }
                }
            imageReader.setOnImageAvailableListener(readerListener,mBackgroundHandler)
            var captureListener : CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession,request: CaptureRequest,result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    createCameraPreview()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cameraDevice!!.createCaptureSession(outputSurface, object : CameraCaptureSession.StateCallback(){
                    override fun onConfigured(session: CameraCaptureSession) {
                        try{
                            session.capture(captureBuilder.build(),captureListener,mBackgroundHandler)
                        }catch (e: CameraAccessException){
                            e.printStackTrace()
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                    }
                },mBackgroundHandler)
            }
        }catch (e : Exception){
            e.printStackTrace()
        }
    }



    fun createCameraPreview(){
        try{
            var texture : SurfaceTexture = textureView.surfaceTexture!!
            assert(texture != null)
            texture.setDefaultBufferSize(imageDimension!!.width,imageDimension!!.height)
            var surface : Surface = Surface(texture)
            captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder!!.addTarget(surface)
            cameraDevice!!.createCaptureSession(
                Arrays.asList(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (null == cameraDevice) {
                            return
                        }
                        cameraCaptureSessions = cameraCaptureSession
                        updatePreview()
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(
                            this@MainActivity,
                            "Configuration change",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                null
            )

        }catch (e : CameraAccessException){
            e.printStackTrace()
        }
    }

    fun openCamera(){
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try{
            cameraId = manager.cameraIdList[0]
            var characteristics : CameraCharacteristics = manager.getCameraCharacteristics(cameraId)
            var map : StreamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            assert(map != null)
            imageDimension = map.getOutputSizes(SurfaceTexture::class.java)[0]

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            manager.openCamera(cameraId,stateCallback,null)
//            manager.openCamera(cameraId, stateCallback, null);

        }catch (e : CameraAccessException){
            e.printStackTrace()
        }
    }
//

    protected fun updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return")
        }
        captureRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try {
            cameraCaptureSessions!!.setRepeatingRequest(
                captureRequestBuilder!!.build(),
                null,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun closeCamera(){
        if (imageReader != null){
            imageReader!!.close()
            imageReader = null
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if(textureView.isAvailable){
            openCamera()
        }else{
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
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

    @Synchronized
    fun GetRotatedBitmap(bitmap: Bitmap?, degrees: Int): Bitmap? {
        var bitmap = bitmap
     if(degrees != 0 && bitmap != null){
         var m : Matrix = Matrix()
         m.setRotate(degrees.toFloat(),(bitmap.width / 2).toFloat(), (bitmap.height /2).toFloat())
         var b2 : Bitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,m,true)
         try{
             if (bitmap != b2) {
                 bitmap = b2
             }
         }catch (e : OutOfMemoryError){
             e.printStackTrace()
         }
     }
        return bitmap
    }


    fun AsyncTess(mRelativeParams : Bitmap?){
        GlobalScope.launch {
        tessBaseAPI.setImage(mRelativeParams)
            var result = tessBaseAPI.utF8Text
            runOnUiThread {
                Log.d("resultData", result.toString())
                var match : String = "[^\uAC00-\uD7A3xfe0-9a-zA-Z\\s]"
                result = result.replace(match,"")
                result = result.replace(" ","")
                if(result .length >= 7 && result.length <= 8){
                    textView.text = result
                    Toast.makeText(this@MainActivity,""+result,Toast.LENGTH_SHORT).show()
                }else{
                    textView.text = "인식 실패"
                    Toast.makeText(this@MainActivity,"이미지가 정확하지 않습니다",Toast.LENGTH_SHORT).show()
                }
                btnTakePicture.isEnabled = true
                btnTakePicture.setText("텍스트 인식")
            }
        }

    }
}