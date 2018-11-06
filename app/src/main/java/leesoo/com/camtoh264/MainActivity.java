package leesoo.com.camtoh264;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import com.hjq.permissions.OnPermission;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;


public class MainActivity extends AppCompatActivity {
    private TextureView camview,h264decodecview;
   private Camera mCamera;
    private int mPreviewWidth;
    private int mPreviewHeight;

    //引用了自己创建的编码解码2个类
   private VideoDecoder mVideoDecoder;
   private VideoEncoder mVideoEncoder;
    private  MediaCodec mCodec;
   // private String h264Path = "720pq.h264";
    private String h264Path = "/storage/emulated/0/720pq.h264";
    private String rootpath = Environment.getExternalStorageDirectory().getAbsolutePath();  //路径
    //private File h264File = new File(rootpath+h264Path);
    private File h264File = new File(h264Path);
    private InputStream is = null;
    private FileInputStream fs = null;
    Thread readFileThread;
    private final static int HEAD_OFFSET = 512;

    //This video stream format must be I420
   // private final static ArrayBlockingQueue<byte []> mInputDatasQueue = new ArrayBlockingQueue<byte []>(CACHE_BUFFER_SIZE);
    //Cachhe video stream which has been encoded.
    //private final static ArrayBlockingQueue<byte []> mOutputDatasQueue = new ArrayBlockingQueue<byte[]>(CACHE_BUFFER_SIZE);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermission();
        initView();


    }
    private void initView() {
        camview = (TextureView) findViewById(R.id.camView);
        h264decodecview = (TextureView) findViewById(R.id.h264decodecView);
        camview.setSurfaceTextureListener(camviewTL);
        h264decodecview.setSurfaceTextureListener(h264decodecviewTL);
    }

    private TextureView.SurfaceTextureListener camviewTL = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                openCamera(surfaceTexture,i,i1);
            //看图预览过程中创建对象，并启动编码器
            mVideoEncoder = new VideoEncoder("video/avc", mPreviewWidth, mPreviewHeight);
            mVideoEncoder.startEncoder();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            mCamera.stopPreview();
            mCamera.release();
            //不用时候关编码器，不然摄像头不工作没数据了，他还在编空数据，报错。
            if(mVideoEncoder != null){
                mVideoEncoder.stopEncoder();
                mVideoEncoder.release();
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private TextureView.SurfaceTextureListener h264decodecviewTL = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {


       mVideoDecoder = new VideoDecoder("video/avc", new Surface(surfaceTexture), mPreviewWidth, mPreviewHeight);
            mVideoDecoder.setEncoder(mVideoEncoder);
            mVideoDecoder.startDecoder();
//            try {
//
//
////                mCodec = MediaCodec.createDecoderByType("video/avc");
////                MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",
////                        mPreviewWidth, mPreviewHeight);
////
////                mCodec.configure(mediaFormat, new Surface(surfaceTexture),
////                        null, 0);
////                mCodec.start();
//
//
//            } catch (IOException e) {
//               // Log.e(TAG, Log.getStackTraceString(e));
//                mCodec = null;
//                return;
//            }

          Log.d("MainActivity", "mCodec.start()--------------------------------" + 1);

            readFileThread = new Thread(readFile);
            readFileThread.start();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
//            mVideoDecoder.stopDecoder();
//            mVideoDecoder.release();
//            mCamera.stopPreview();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };
    private Camera.PreviewCallback mPreviewCallBack = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            byte[] YUV21 = new byte[bytes.length];
            byte[] i420bytes = new byte[bytes.length];
               // byte[] yuv = new byte[mPreviewWidth * mPreviewHeight * 3 / 2];

            //from YV20 to i420
//            System.arraycopy(bytes, 0, i420bytes, 0, mPreviewWidth * mPreviewHeight);
//            System.arraycopy(bytes, mPreviewWidth * mPreviewHeight + mPreviewWidth * mPreviewHeight / 4, i420bytes, mPreviewWidth * mPreviewHeight, mPreviewWidth * mPreviewHeight / 4);
//            System.arraycopy(bytes, mPreviewWidth * mPreviewHeight, i420bytes, mPreviewWidth * mPreviewHeight + mPreviewWidth * mPreviewHeight / 4, mPreviewWidth * mPreviewHeight / 4);
            //用到了这个mVideoEncoder类的获取数据方法，把数据放到解码器
            if(mVideoEncoder != null) {
                mVideoEncoder.inputFrameToEncoder(YUV21);
            }
          //  boolean inputResult = mInputDatasQueue.offer(i420bytes);
        }
    };

    private void openCamera(SurfaceTexture texture,int width, int height){
        {
           // Camera mCamera = null;
            mCamera = Camera.open(0);
            try {
                mCamera.setPreviewTexture(texture);
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewFormat(ImageFormat.YV12);
                List<Camera.Size> list = parameters.getSupportedPreviewSizes();
                for (Camera.Size size : list) {
                    System.out.println("----size width = " + size.width + " size height = " + size.height);
                }

                mPreviewWidth = 640;
                mPreviewHeight = 480;
                parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);
                mCamera.setParameters(parameters);
                mCamera.setPreviewCallback(mPreviewCallBack);
                mCamera.startPreview();
            } catch (IOException e) {
                Log.e("CAM---------", Log.getStackTraceString(e));
                mCamera = null;
            }
        }

    }
    public void requestPermission() {
        XXPermissions.with(this)
                //.constantRequest() //可设置被拒绝后继续申请，直到用户授权或者永久拒绝
               // .permission(Permission.SYSTEM_ALERT_WINDOW, Permission.REQUEST_INSTALL_PACKAGES) //支持请求6.0悬浮窗权限8.0请求安装权限
               // .permission(Permission.Group.STORAGE, Permission.Group.CALENDAR,Permission.Group.LOCATION) //不指定权限则自动获取清单中的危险权限
                .permission(Permission.CAMERA,Permission.WRITE_EXTERNAL_STORAGE,Permission.READ_EXTERNAL_STORAGE)
                .request(new OnPermission() {

                    @Override
                    public void hasPermission(List<String> granted, boolean isAll) {
                        if (isAll) {
                           // ToastUtils.show("获取权限成功");
                        }else {
                           // ToastUtils.show("获取权限成功，部分权限未正常授予");
                        }
                    }

                    @Override
                    public void noPermission(List<String> denied, boolean quick) {
                        if(quick) {
                           // ToastUtils.show("被永久拒绝授权，请手动授予权限");
                            //如果是被永久拒绝就跳转到应用权限系统设置页面
                            XXPermissions.gotoPermissionSettings(MainActivity.this);
                        }else {
                            //ToastUtils.show("获取权限失败");
                        }
                    }
                });
    }

    Runnable readFile = new Runnable() {

        @Override
        public void run() {
            int h264Read = 0;
            int frameOffset = 0;
            byte[] buffer = new byte[100000];
            byte[] framebuffer = new byte[200000];
            boolean readFlag = true;
            try {
                fs = new FileInputStream(h264File);
                is = new BufferedInputStream(fs);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            while (!Thread.interrupted() && readFlag) {
                try {
                    int length = is.available();
                    if (length > 0) {
                        // Read file and fill buffer
                        int count = is.read(buffer);
                        Log.i("count", "" + count);
                        h264Read += count;//h264Read=h264Read+count
                        Log.d("Read", "count:" + count + " h264Read:"
                                + h264Read);
                        // Fill frameBuffer
                        if (frameOffset + count < 200000) {
                            System.arraycopy(buffer, 0, framebuffer,
                                    frameOffset, count);
                            frameOffset += count;
                        } else {
                            frameOffset = 0;
                            System.arraycopy(buffer, 0, framebuffer,
                                    frameOffset, count);
                            frameOffset += count;
                        }

                        // Find H264 head
                        int offset = findHead(framebuffer, frameOffset);
                        Log.i("find head", " Head:" + offset);
                        while (offset > 0) {
                            if (checkHead(framebuffer, 0)) {
                                // Fill decoder

                                boolean flag = mVideoEncoder.pollH264tobuffer(framebuffer);
                              //  mVideoEncoder.pollFrameFromEncoder();

                                if (flag) {
                                    byte[] temp = framebuffer;
                                    framebuffer = new byte[200000];
                                    System.arraycopy(temp, offset, framebuffer,
                                            0, frameOffset - offset);
                                    frameOffset -= offset;
                                    Log.e("Check", "is Head:" + offset);
                                    // Continue finding head
                                    offset = findHead(framebuffer, frameOffset);
                                }
                            } else {

                                offset = 0;
                            }

                        }
                        Log.d("loop", "end loop");
                    } else {
                        h264Read = 0;
                        frameOffset = 0;
                        readFlag = false;
                        // Start a new thread
                        readFileThread = new Thread(readFile);
                        readFileThread.start();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {

                }
            }
        }
    };

//    public   byte[] h264todecodec(byte[] freame){
//
//      return   freame ;
//    }

    /**
     * Find H264 frame head
     *
     * @param buffer
     * @param len
     * @return the offset of frame head, return 0 if can not find one
     */
    static int findHead(byte[] buffer, int len) {
        int i;
        for (i = HEAD_OFFSET; i < len; i++) {
            if (checkHead(buffer, i))
                break;
        }
        if (i == len)
            return 0;
        if (i == HEAD_OFFSET)
            return 0;
        return i;
    }

    /**
     * Check if is H264 frame head
     *
     * @param buffer
     * @param offset
     * @return whether the src buffer is frame head
     */
    static boolean checkHead(byte[] buffer, int offset) {
        // 00 00 00 01
        if (buffer[offset] == 0 && buffer[offset + 1] == 0
                && buffer[offset + 2] == 0 && buffer[3] == 1)
            return true;
        // 00 00 01
        if (buffer[offset] == 0 && buffer[offset + 1] == 0
                && buffer[offset + 2] == 1)
            return true;
        return false;
    }
    int mCount = 0;
    public boolean onFrame(byte[] buf, int offset, int length) {
        Log.e("Media", "onFrame start");
        Log.e("Media", "onFrame Thread:" + Thread.currentThread().getId());
        // Get input buffer index
        ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
        int inputBufferIndex = mCodec.dequeueInputBuffer(100);

        Log.e("Media", "onFrame index:" + inputBufferIndex);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(buf, offset, length);
            mCodec.queueInputBuffer(inputBufferIndex, 0, length, mCount
                    * 30, 0);
            mCount++;
        } else {
            return false;
        }

        // Get output buffer index
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);
        while (outputBufferIndex >= 0) {
            mCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
        Log.e("Media", "onFrame end");
        return true;
    }
}
